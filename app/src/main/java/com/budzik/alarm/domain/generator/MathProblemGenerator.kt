package com.budzik.alarm.domain.generator

import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.domain.model.MathProblem
import kotlin.random.Random

/**
 * Production implementation of [IMathProblemGenerator].
 *
 * Implements progressive arithmetic missions for alarm dismissal with strict digit constraints:
 * - [Difficulty.EASY]: Single-digit addition (operands in 1..9, sum in 2..18)
 * - [Difficulty.MEDIUM]: Two-digit addition (operands in 10..99, sum in 20..198)
 * - [Difficulty.HARD]: Four-digit addition (operands in 1000..9999, sum in 2000..19998)
 *
 * Supports injectable [Random] for reproducible testing and deterministic test sequences.
 * Also provides a static singleton instance via companion object for zero-overhead access
 * and reflection compatibility with [com.budzik.alarm.e2e.TestComponentFactory].
 */
class MathProblemGenerator(
    private val random: Random = Random.Default
) : IMathProblemGenerator {

    override fun generateProblem(difficulty: Difficulty): MathProblem {
        val (operandA, operandB) = when (difficulty) {
            Difficulty.EASY -> Pair(
                random.nextInt(1, 10),
                random.nextInt(1, 10)
            )
            Difficulty.MEDIUM -> Pair(
                random.nextInt(10, 100),
                random.nextInt(10, 100)
            )
            Difficulty.HARD -> Pair(
                random.nextInt(1000, 10000),
                random.nextInt(1000, 10000)
            )
        }
        val solution = operandA + operandB
        return MathProblem(
            operandA = operandA,
            operandB = operandB,
            solution = solution
        )
    }

    override fun validateAnswer(problem: MathProblem, answer: Int): Boolean {
        val expectedSum = problem.operandA + problem.operandB
        return answer == problem.solution && problem.solution == expectedSum
    }

    companion object : IMathProblemGenerator {
        /**
         * Singleton instance exposed as a JVM static field for reflection access
         * in test factories and dependency injection defaults.
         */
        @JvmField
        val INSTANCE: MathProblemGenerator = MathProblemGenerator()

        override fun generateProblem(difficulty: Difficulty): MathProblem =
            INSTANCE.generateProblem(difficulty)

        override fun validateAnswer(problem: MathProblem, answer: Int): Boolean =
            INSTANCE.validateAnswer(problem, answer)
    }
}
