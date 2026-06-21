/*
 * The Compukter Kraft Developers
 *
 * Copyright (C) 2026 Vsevolod Petrov (lazyhat)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.lazyhat.kraftui.foundation

import kotlin.reflect.KProperty1

sealed interface GeneratedValueExpression {
    data class Constant(
        val value: Any?,
    ) : GeneratedValueExpression

    data class StateField(
        val fieldName: String,
    ) : GeneratedValueExpression

    data class And(
        val terms: List<GeneratedValueExpression>,
    ) : GeneratedValueExpression

    data class Match(
        val subject: GeneratedValueExpression,
        val cases: Map<Any?, GeneratedValueExpression>,
        val default: GeneratedValueExpression,
    ) : GeneratedValueExpression
}

interface Value<out T> {
    val value: T
    val isStatic: Boolean
        get() = false
    val generatedExpression: GeneratedValueExpression?
        get() = null
}

@JvmInline
value class ValueConstant<T>(
    override val value: T,
) : Value<T> {
    override val isStatic: Boolean
        get() = true
    override val generatedExpression: GeneratedValueExpression
        get() = GeneratedValueExpression.Constant(value)
}

private class ValueExpression<T>(
    private val block: () -> T,
) : Value<T> {
    override val value: T
        get() = block()
}

private class TickValueExpression<T>(
    private val block: (Int) -> T,
) : Value<T> {
    override val value: T
        get() = block(TickContext.current)
}

private class StateValueExpression<State, T>(
    private val property: KProperty1<State, T>,
    private val state: () -> State,
) : Value<T> {
    override val value: T
        get() = property.get(state())

    override val generatedExpression: GeneratedValueExpression
        get() = GeneratedValueExpression.StateField(property.name)
}

private class AndValueExpression(
    val terms: List<Value<Boolean>>,
) : Value<Boolean> {
    override val value: Boolean
        get() = terms.all { it.value }

    override val isStatic: Boolean
        get() = terms.all { it.isStatic }

    override val generatedExpression: GeneratedValueExpression?
        get() {
            val generatedTerms = terms.map { it.generatedExpression ?: return null }
            return GeneratedValueExpression.And(
                generatedTerms.flatMap {
                    when (it) {
                        is GeneratedValueExpression.And -> it.terms
                        else -> listOf(it)
                    }
                },
            )
        }
}

private class MatchValueExpression<K, T>(
    private val subject: Value<K>,
    private val cases: Map<K, Value<T>>,
    private val default: Value<T>,
) : Value<T> {
    override val value: T
        get() = cases[subject.value]?.value ?: default.value

    override val isStatic: Boolean
        get() = subject.isStatic && cases.values.all { it.isStatic } && default.isStatic

    override val generatedExpression: GeneratedValueExpression?
        get() {
            val subjectExpression = subject.generatedExpression ?: return null
            val generatedCases =
                cases.entries.associate { (key, value) ->
                    key as Any? to (value.generatedExpression ?: return null)
                }
            val defaultExpression = default.generatedExpression ?: return null
            return GeneratedValueExpression.Match(
                subject = subjectExpression,
                cases = generatedCases,
                default = defaultExpression,
            )
        }
}

fun <T> value(value: T): Value<T> = ValueConstant(value)

fun <T> value(block: () -> T): Value<T> = ValueExpression(block)

fun <State, T> stateValue(
    property: KProperty1<State, T>,
    state: () -> State,
): Value<T> = StateValueExpression(property, state)

internal fun andValues(
    left: Value<Boolean>?,
    right: Value<Boolean>?,
): Value<Boolean>? =
    when {
        left == null -> right
        right == null -> left
        else ->
            AndValueExpression(
                buildList {
                    fun addTerm(value: Value<Boolean>) {
                        if (value is AndValueExpression) {
                            addAll(value.terms)
                        } else {
                            add(value)
                        }
                    }
                    addTerm(left)
                    addTerm(right)
                },
            )
    }

/**
 * A [Value] whose computation receives the current monotonic UI tick. The tick
 * counter is incremented by [ru.lazyhat.kraftui.program.ScreenRuntimeExecutor.render]
 * before walking render ops. Useful for blink/animation primitives, e.g.:
 *
 *     val cursorVisible = tickValue { it / 6 % 2 == 0 }
 */
fun <T> tickValue(block: (Int) -> T): Value<T> = TickValueExpression(block)

fun <K, T> matchValue(
    subject: Value<K>,
    cases: Map<K, Value<T>>,
    default: Value<T>,
): Value<T> {
    require(cases.isNotEmpty()) { "matchValue must have at least one case" }
    return MatchValueExpression(subject, cases, default)
}

/**
 * Holds the current monotonic UI tick. Updated by
 * [ru.lazyhat.kraftui.program.ScreenRuntimeExecutor.render] just
 * before rendering each frame. Read-only for DSL users; expose only through
 * [tickValue].
 */
object TickContext {
    var current: Int = 0
        internal set
}
