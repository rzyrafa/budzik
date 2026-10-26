package com.budzik.alarm.domain

import com.budzik.alarm.domain.generator.IMathProblemGenerator
import com.budzik.alarm.domain.generator.MathProblemGenerator
import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.domain.model.MathProblem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * Unit Test Suite for [MathProblemGenerator].
 *
 * Verifies:
 * 1. Strict digit range constraints across 200+ iterations:
 *    - Easy: operands in [1..9], solution in [2..18]
 *    - Medium: operands in [10..99], solution in [20..198]
 *    - Hard: operands in [1000..9999], solution in [2000..19998]
 * 2. Arithmetic correctness: solution == operandA + operandB for all generated problems.
 * 3. Answer validation: correct answers, off-by-one, zero, negative, swapped digits, and extreme values.
 * 4. Determinism & reproducibility via injectable [Random].
 * 5. Random distribution and entropy over 100+ iterations.
 * 6. Singleton and reflection compatibility with [com.budzik.alarm.e2e.TestComponentFactory].
 */
class MathProblemGeneratorTest {

    private lateinit var generator: MathProblemGenerator

    @Before
    fun setUp() {
        generator = MathProblemGenerator()
    }

    // =========================================================================
    // 1. EASY TIER: 1-Digit Addition (operands in 1..9, sum in 2..18)
    // =========================================================================

    @Test
    fun testEasy_operandsStrictlyBetween1And9_across200Iterations() {
        for (i in 1..200) {
            val problem = generator.generateProblem(Difficulty.EASY)
            assertTrue("Easy operandA must be >= 1, got ${problem.operandA}", problem.operandA >= 1)
            assertTrue("Easy operandA must be <= 9, got ${problem.operandA}", problem.operandA <= 9)
            assertTrue("Easy operandB must be >= 1, got ${problem.operandB}", problem.operandB >= 1)
            assertTrue("Easy operandB must be <= 9, got ${problem.operandB}", problem.operandB <= 9)
        }
    }

    @Test
    fun testEasy_solutionEqualsSum_andBoundedBetween2And18() {
        for (i in 1..200) {
            val problem = generator.generateProblem(Difficulty.EASY)
            assertEquals(
                "Easy solution must equal operandA + operandB",
                problem.operandA + problem.operandB,
                problem.solution
            )
            assertTrue("Easy solution must be >= 2, got ${problem.solution}", problem.solution >= 2)
            assertTrue("Easy solution must be <= 18, got ${problem.solution}", problem.solution <= 18)
        }
    }

    @Test
    fun testEasy_validateAnswer_correctAnswerReturnsTrue() {
        val problem = MathProblem(operandA = 4, operandB = 5, solution = 9)
        assertTrue(generator.validateAnswer(problem, 9))
    }

    @Test
    fun testEasy_validateAnswer_incorrectAnswersReturnFalse() {
        val problem = MathProblem(operandA = 3, operandB = 6, solution = 9)
        assertFalse("Off-by-one below", generator.validateAnswer(problem, 8))
        assertFalse("Off-by-one above", generator.validateAnswer(problem, 10))
        assertFalse("Zero answer", generator.validateAnswer(problem, 0))
        assertFalse("Negative answer", generator.validateAnswer(problem, -9))
    }

    @Test
    fun testEasy_minAndMaxBoundarySums() {
        val minProblem = MathProblem(operandA = 1, operandB = 1, solution = 2)
        assertTrue("1 + 1 = 2 must validate", generator.validateAnswer(minProblem, 2))

        val maxProblem = MathProblem(operandA = 9, operandB = 9, solution = 18)
        assertTrue("9 + 9 = 18 must validate", generator.validateAnswer(maxProblem, 18))
    }

    // =========================================================================
    // 2. MEDIUM TIER: 2-Digit Addition (operands in 10..99, sum in 20..198)
    // =========================================================================

    @Test
    fun testMedium_operandsStrictlyBetween10And99_across200Iterations() {
        for (i in 1..200) {
            val problem = generator.generateProblem(Difficulty.MEDIUM)
            assertTrue("Medium operandA must be >= 10, got ${problem.operandA}", problem.operandA >= 10)
            assertTrue("Medium operandA must be <= 99, got ${problem.operandA}", problem.operandA <= 99)
            assertTrue("Medium operandB must be >= 10, got ${problem.operandB}", problem.operandB >= 10)
            assertTrue("Medium operandB must be <= 99, got ${problem.operandB}", problem.operandB <= 99)
        }
    }

    @Test
    fun testMedium_solutionEqualsSum_andBoundedBetween20And198() {
        for (i in 1..200) {
            val problem = generator.generateProblem(Difficulty.MEDIUM)
            assertEquals(
                "Medium solution must equal operandA + operandB",
                problem.operandA + problem.operandB,
                problem.solution
            )
            assertTrue("Medium solution must be >= 20, got ${problem.solution}", problem.solution >= 20)
            assertTrue("Medium solution must be <= 198, got ${problem.solution}", problem.solution <= 198)
        }
    }

    @Test
    fun testMedium_validateAnswer_correctAnswerReturnsTrue() {
        val problem = MathProblem(operandA = 47, operandB = 38, solution = 85)
        assertTrue(generator.validateAnswer(problem, 85))
    }

    @Test
    fun testMedium_validateAnswer_incorrectAnswersReturnFalse() {
        val problem = MathProblem(operandA = 25, operandB = 55, solution = 80)
        assertFalse("Off-by-one below", generator.validateAnswer(problem, 79))
        assertFalse("Off-by-one above", generator.validateAnswer(problem, 81))
        assertFalse("Truncated digit", generator.validateAnswer(problem, 8))
        assertFalse("Negative sum", generator.validateAnswer(problem, -80))
    }

    @Test
    fun testMedium_minAndMaxBoundarySums() {
        val minProblem = MathProblem(operandA = 10, operandB = 10, solution = 20)
        assertTrue("10 + 10 = 20 must validate", generator.validateAnswer(minProblem, 20))

        val maxProblem = MathProblem(operandA = 99, operandB = 99, solution = 198)
        assertTrue("99 + 99 = 198 must validate", generator.validateAnswer(maxProblem, 198))
    }

    // =========================================================================
    // 3. HARD TIER: 4-Digit Addition (operands in 1000..9999, sum in 2000..19998)
    // =========================================================================

    @Test
    fun testHard_operandsStrictlyBetween1000And9999_across200Iterations() {
        for (i in 1..200) {
            val problem = generator.generateProblem(Difficulty.HARD)
            assertTrue("Hard operandA must be >= 1000, got ${problem.operandA}", problem.operandA >= 1000)
            assertTrue("Hard operandA must be <= 9999, got ${problem.operandA}", problem.operandA <= 9999)
            assertTrue("Hard operandB must be >= 1000, got ${problem.operandB}", problem.operandB >= 1000)
            assertTrue("Hard operandB must be <= 9999, got ${problem.operandB}", problem.operandB <= 9999)
        }
    }

    @Test
    fun testHard_solutionEqualsSum_andBoundedBetween2000And19998() {
        for (i in 1..200) {
            val problem = generator.generateProblem(Difficulty.HARD)
            assertEquals(
                "Hard solution must equal operandA + operandB",
                problem.operandA + problem.operandB,
                problem.solution
            )
            assertTrue("Hard solution must be >= 2000, got ${problem.solution}", problem.solution >= 2000)
            assertTrue("Hard solution must be <= 19998, got ${problem.solution}", problem.solution <= 19998)
        }
    }

    @Test
    fun testHard_validateAnswer_correctAnswerReturnsTrue() {
        val problem = MathProblem(operandA = 4829, operandB = 3171, solution = 8000)
        assertTrue(generator.validateAnswer(problem, 8000))
    }

    @Test
    fun testHard_validateAnswer_incorrectAnswersReturnFalse() {
        val problem = MathProblem(operandA = 1234, operandB = 5678, solution = 6912)
        assertFalse("Swapped digits (6921)", generator.validateAnswer(problem, 6921))
        assertFalse("Reversed digits (2196)", generator.validateAnswer(problem, 2196))
        assertFalse("Off-by-one below (6911)", generator.validateAnswer(problem, 6911))
        assertFalse("Off-by-one above (6913)", generator.validateAnswer(problem, 6913))
        assertFalse("Negative answer (-6912)", generator.validateAnswer(problem, -6912))
    }

    @Test
    fun testHard_minAndMaxBoundarySums() {
        val minProblem = MathProblem(operandA = 1000, operandB = 1000, solution = 2000)
        assertTrue("1000 + 1000 = 2000 must validate", generator.validateAnswer(minProblem, 2000))

        val maxProblem = MathProblem(operandA = 9999, operandB = 9999, solution = 19998)
        assertTrue("9999 + 9999 = 19998 must validate", generator.validateAnswer(maxProblem, 19998))
    }

    // =========================================================================
    // 4. RANDOMNESS, DETERMINISM & DISTRIBUTION
    // =========================================================================

    @Test
    fun testDeterministicRng_withFixedSeed_producesIdenticalSequence() {
        val seed = 987654321L
        val gen1 = MathProblemGenerator(Random(seed))
        val gen2 = MathProblemGenerator(Random(seed))

        for (difficulty in listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)) {
            for (i in 1..50) {
                val p1 = gen1.generateProblem(difficulty)
                val p2 = gen2.generateProblem(difficulty)
                assertEquals("Deterministic operandA mismatch at iteration $i", p1.operandA, p2.operandA)
                assertEquals("Deterministic operandB mismatch at iteration $i", p1.operandB, p2.operandB)
                assertEquals("Deterministic solution mismatch at iteration $i", p1.solution, p2.solution)
            }
        }
    }

    @Test
    fun testDifferentSeeds_produceDivergentSequences() {
        val gen1 = MathProblemGenerator(Random(11111L))
        val gen2 = MathProblemGenerator(Random(22222L))

        var differences = 0
        for (i in 1..30) {
            val p1 = gen1.generateProblem(Difficulty.MEDIUM)
            val p2 = gen2.generateProblem(Difficulty.MEDIUM)
            if (p1.operandA != p2.operandA || p1.operandB != p2.operandB) {
                differences++
            }
        }
        assertTrue("Different seeds must produce different problem sequences", differences > 20)
    }

    @Test
    fun testRandomDistribution_producesDiverseOperandsAcrossIterations() {
        // Easy: 9 possible operands (1..9). In 150 iterations, we expect at least 7 unique values.
        val easyOperandsA = mutableSetOf<Int>()
        val easyOperandsB = mutableSetOf<Int>()
        for (i in 1..150) {
            val p = generator.generateProblem(Difficulty.EASY)
            easyOperandsA.add(p.operandA)
            easyOperandsB.add(p.operandB)
        }
        assertTrue("Easy operandA must have high entropy, got ${easyOperandsA.size} unique values", easyOperandsA.size >= 7)
        assertTrue("Easy operandB must have high entropy, got ${easyOperandsB.size} unique values", easyOperandsB.size >= 7)

        // Medium: 90 possible operands (10..99). In 150 iterations, expect >= 40 unique values.
        val mediumOperands = mutableSetOf<Int>()
        for (i in 1..150) {
            val p = generator.generateProblem(Difficulty.MEDIUM)
            mediumOperands.add(p.operandA)
            mediumOperands.add(p.operandB)
        }
        assertTrue("Medium operands must have high entropy, got ${mediumOperands.size} unique values", mediumOperands.size >= 40)

        // Hard: 9000 possible operands (1000..9999). In 150 iterations, expect >= 100 unique values.
        val hardOperands = mutableSetOf<Int>()
        for (i in 1..150) {
            val p = generator.generateProblem(Difficulty.HARD)
            hardOperands.add(p.operandA)
            hardOperands.add(p.operandB)
        }
        assertTrue("Hard operands must have high entropy, got ${hardOperands.size} unique values", hardOperands.size >= 100)
    }

    // =========================================================================
    // 5. DEFENSIVE PROGRAMMING & BOUNDARY SAFETY
    // =========================================================================

    @Test
    fun testValidateAnswer_overflowAndExtremeIntegers_safeHandling() {
        val problem = MathProblem(operandA = 5000, operandB = 5000, solution = 10000)
        assertFalse("Int.MAX_VALUE must not crash and return false", generator.validateAnswer(problem, Int.MAX_VALUE))
        assertFalse("Int.MIN_VALUE must not crash and return false", generator.validateAnswer(problem, Int.MIN_VALUE))
        assertFalse("Int.MAX_VALUE - 1 must return false", generator.validateAnswer(problem, Int.MAX_VALUE - 1))
    }

    @Test
    fun testValidateAnswer_corruptedInternalStateProblem_returnsFalse() {
        // If a MathProblem has an inconsistent solution field (e.g. 10 + 10 != 25)
        val corruptedProblem = MathProblem(operandA = 10, operandB = 10, solution = 25)
        // Even if user answers 25 (matching problem.solution), validation fails because 10 + 10 != 25
        assertFalse(
            "Validation must fail if problem internal sum is mathematically invalid",
            generator.validateAnswer(corruptedProblem, 25)
        )
    }

    // =========================================================================
    // 6. SINGLETON & REFLECTION COMPATIBILITY
    // =========================================================================

    @Test
    fun testCompanionObject_staticDelegation() {
        val easy = MathProblemGenerator.generateProblem(Difficulty.EASY)
        assertTrue(easy.operandA in 1..9)
        assertTrue(easy.operandB in 1..9)
        assertEquals(easy.operandA + easy.operandB, easy.solution)

        val isValid = MathProblemGenerator.validateAnswer(easy, easy.solution)
        assertTrue(isValid)
    }

    @Test
    fun testCompanionObject_fieldInstanceAccessibleViaReflection() {
        val clazz = Class.forName("com.budzik.alarm.domain.generator.MathProblemGenerator")
        val field = clazz.getDeclaredField("INSTANCE")
        assertNotNull("Field INSTANCE must exist on MathProblemGenerator class", field)

        val instance = field.get(null)
        assertNotNull("INSTANCE value must not be null", instance)
        assertTrue("INSTANCE must implement IMathProblemGenerator", instance is IMathProblemGenerator)

        val mathGen = instance as IMathProblemGenerator
        val problem = mathGen.generateProblem(Difficulty.HARD)
        assertTrue(problem.operandA in 1000..9999)
        assertTrue(problem.operandB in 1000..9999)
        assertTrue(mathGen.validateAnswer(problem, problem.solution))
    }
}
