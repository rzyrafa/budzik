package com.budzik.alarm.domain

import com.budzik.alarm.domain.generator.MathProblemGenerator
import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.domain.model.MathProblem
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Empirical Adversarial Stress Test Suite for [MathProblemGenerator].
 *
 * Authored by: Challenger 1 (Milestone 2)
 *
 * Objectives:
 * 1. Generate 300,000+ problems across multiple deterministic seeds and Default RNG.
 * 2. Empirically verify strict invariant enforcement:
 *    - EASY: operandA in 1..9, operandB in 1..9, solution == a + b in 2..18.
 *    - MEDIUM: operandA in 10..99, operandB in 10..99, solution == a + b in 20..198.
 *    - HARD: operandA in 1000..9999, operandB in 1000..9999, solution == a + b in 2000..19998.
 *    - Strictly zero leakage: no zeros, negative numbers, or boundary transgressions.
 * 3. Verify boundary coverage: guarantees that min and max boundaries are reached.
 * 4. Verify uniform distribution / no starved buckets.
 * 5. Verify oracle robustness against mathematical tampering and adversarial inputs.
 * 6. Verify concurrent execution thread-safety under multi-threaded contention.
 */
class MathProblemGeneratorAdversarialTest {

    private val testSeeds = listOf(
        0L,
        1L,
        42L,
        123456789L,
        -1L,
        -987654321L,
        Long.MAX_VALUE,
        Long.MIN_VALUE,
        3141592653589793L,
        2718281828459045L
    )

    // =========================================================================
    // 1. EASY TIER: 100,000+ Iterations Across 10 Seeds + Default RNG
    // =========================================================================

    @Test
    fun testEasy_stressTest_100kProblems_invariantsAndFullCoverage() {
        val generators = testSeeds.map { MathProblemGenerator(Random(it)) } + MathProblemGenerator.INSTANCE

        var totalTested = 0
        var minA = Int.MAX_VALUE
        var maxA = Int.MIN_VALUE
        var minB = Int.MAX_VALUE
        var maxB = Int.MIN_VALUE
        var minSum = Int.MAX_VALUE
        var maxSum = Int.MIN_VALUE

        val pairCoverage = BooleanArray(10 * 10) // 1..9 for A and B
        val digitCountsA = IntArray(10) // 1..9

        for (gen in generators) {
            for (i in 1..10_000) {
                val problem = gen.generateProblem(Difficulty.EASY)
                totalTested++

                val a = problem.operandA
                val b = problem.operandB
                val sum = problem.solution

                // Invariant checks
                assertTrue("Easy operandA must be >= 1, was $a", a >= 1)
                assertTrue("Easy operandA must be <= 9, was $a", a <= 9)
                assertTrue("Easy operandB must be >= 1, was $b", b >= 1)
                assertTrue("Easy operandB must be <= 9, was $b", b <= 9)
                assertEquals("Easy sum must exactly equal a + b ($a + $b != $sum)", a + b, sum)
                assertTrue("Easy sum must be in 2..18, was $sum", sum in 2..18)

                // Oracle validation checks
                assertTrue("Oracle must validate correct answer", gen.validateAnswer(problem, sum))
                assertFalse("Oracle must reject sum + 1", gen.validateAnswer(problem, sum + 1))
                assertFalse("Oracle must reject sum - 1", gen.validateAnswer(problem, sum - 1))
                assertFalse("Oracle must reject 0", gen.validateAnswer(problem, 0))
                assertFalse("Oracle must reject negative sum", gen.validateAnswer(problem, -sum))

                // Stats collection
                if (a < minA) minA = a
                if (a > maxA) maxA = a
                if (b < minB) minB = b
                if (b > maxB) maxB = b
                if (sum < minSum) minSum = sum
                if (sum > maxSum) maxSum = sum

                pairCoverage[a * 10 + b] = true
                digitCountsA[a]++
            }
        }

        // Verify boundary limits reached
        assertEquals("Minimum operandA observed must be 1", 1, minA)
        assertEquals("Maximum operandA observed must be 9", 9, maxA)
        assertEquals("Minimum operandB observed must be 1", 1, minB)
        assertEquals("Maximum operandB observed must be 9", 9, maxB)
        assertEquals("Minimum sum observed must be 2", 2, minSum)
        assertEquals("Maximum sum observed must be 18", 18, maxSum)

        // Verify all 81 possible (a, b) pairs in 1..9 were generated
        for (a in 1..9) {
            for (b in 1..9) {
                assertTrue("Pair ($a, $b) was never generated across $totalTested trials", pairCoverage[a * 10 + b])
            }
        }

        // Verify distribution uniformity (each digit 1..9 should get ~11% = ~12,222 hits; verify no starvation)
        for (digit in 1..9) {
            val count = digitCountsA[digit]
            assertTrue("Digit $digit count ($count) should be > 8000 out of $totalTested", count > 8000)
            assertTrue("Digit $digit count ($count) should be < 16000 out of $totalTested", count < 16000)
        }

        println("EASY STRESS TEST PASSED: $totalTested problems verified with 100% invariant adherence and 81/81 pair coverage.")
    }

    // =========================================================================
    // 2. MEDIUM TIER: 100,000+ Iterations Across 10 Seeds + Default RNG
    // =========================================================================

    @Test
    fun testMedium_stressTest_100kProblems_invariantsAndFullCoverage() {
        val generators = testSeeds.map { MathProblemGenerator(Random(it)) } + MathProblemGenerator.INSTANCE

        var totalTested = 0
        var minA = Int.MAX_VALUE
        var maxA = Int.MIN_VALUE
        var minB = Int.MAX_VALUE
        var maxB = Int.MIN_VALUE
        var minSum = Int.MAX_VALUE
        var maxSum = Int.MIN_VALUE

        val observedOperandsA = BooleanArray(100) // 10..99
        val observedOperandsB = BooleanArray(100) // 10..99

        for (gen in generators) {
            for (i in 1..10_000) {
                val problem = gen.generateProblem(Difficulty.MEDIUM)
                totalTested++

                val a = problem.operandA
                val b = problem.operandB
                val sum = problem.solution

                // Invariant checks: strictly 2-digit (10..99)
                assertTrue("Medium operandA must be >= 10, was $a", a >= 10)
                assertTrue("Medium operandA must be <= 99, was $a", a <= 99)
                assertTrue("Medium operandB must be >= 10, was $b", b >= 10)
                assertTrue("Medium operandB must be <= 99, was $b", b <= 99)
                assertEquals("Medium sum must equal a + b ($a + $b != $sum)", a + b, sum)
                assertTrue("Medium sum must be in 20..198, was $sum", sum in 20..198)

                // Oracle validation checks
                assertTrue("Oracle must validate correct answer", gen.validateAnswer(problem, sum))
                assertFalse("Oracle must reject sum + 1", gen.validateAnswer(problem, sum + 1))
                assertFalse("Oracle must reject sum - 1", gen.validateAnswer(problem, sum - 1))
                assertFalse("Oracle must reject 0", gen.validateAnswer(problem, 0))
                assertFalse("Oracle must reject negative sum", gen.validateAnswer(problem, -sum))

                // Stats collection
                if (a < minA) minA = a
                if (a > maxA) maxA = a
                if (b < minB) minB = b
                if (b > maxB) maxB = b
                if (sum < minSum) minSum = sum
                if (sum > maxSum) maxSum = sum

                observedOperandsA[a] = true
                observedOperandsB[b] = true
            }
        }

        // Verify boundary limits reached
        assertEquals("Minimum operandA observed must be 10", 10, minA)
        assertEquals("Maximum operandA observed must be 99", 99, maxA)
        assertEquals("Minimum operandB observed must be 10", 10, minB)
        assertEquals("Maximum operandB observed must be 99", 99, maxB)
        assertEquals("Minimum sum observed must be 20", 20, minSum)
        assertEquals("Maximum sum observed must be 198", 198, maxSum)

        // Verify all 90 distinct values (10..99) were generated for both A and B
        for (v in 10..99) {
            assertTrue("Medium operandA value $v was never generated", observedOperandsA[v])
            assertTrue("Medium operandB value $v was never generated", observedOperandsB[v])
        }

        // Verify no digit leakage into 1-digit or 3-digit
        for (v in 0..9) {
            assertFalse("Leakage! Medium operandA generated 1-digit value $v", observedOperandsA[v])
            assertFalse("Leakage! Medium operandB generated 1-digit value $v", observedOperandsB[v])
        }

        println("MEDIUM STRESS TEST PASSED: $totalTested problems verified with 100% invariant adherence and 90/90 operand coverage.")
    }

    // =========================================================================
    // 3. HARD TIER: 100,000+ Iterations Across 10 Seeds + Default RNG
    // =========================================================================

    @Test
    fun testHard_stressTest_100kProblems_invariantsAndFullCoverage() {
        val generators = testSeeds.map { MathProblemGenerator(Random(it)) } + MathProblemGenerator.INSTANCE

        var totalTested = 0
        var minA = Int.MAX_VALUE
        var maxA = Int.MIN_VALUE
        var minB = Int.MAX_VALUE
        var maxB = Int.MIN_VALUE
        var minSum = Int.MAX_VALUE
        var maxSum = Int.MIN_VALUE

        val uniqueA = HashSet<Int>(9000)
        val uniqueB = HashSet<Int>(9000)

        for (gen in generators) {
            for (i in 1..10_000) {
                val problem = gen.generateProblem(Difficulty.HARD)
                totalTested++

                val a = problem.operandA
                val b = problem.operandB
                val sum = problem.solution

                // Invariant checks: strictly 4-digit (1000..9999)
                assertTrue("Hard operandA must be >= 1000, was $a", a >= 1000)
                assertTrue("Hard operandA must be <= 9999, was $a", a <= 9999)
                assertTrue("Hard operandB must be >= 1000, was $b", b >= 1000)
                assertTrue("Hard operandB must be <= 9999, was $b", b <= 9999)
                assertEquals("Hard sum must equal a + b ($a + $b != $sum)", a + b, sum)
                assertTrue("Hard sum must be in 2000..19998, was $sum", sum in 2000..19998)

                // Oracle validation checks
                assertTrue("Oracle must validate correct answer", gen.validateAnswer(problem, sum))
                assertFalse("Oracle must reject sum + 1", gen.validateAnswer(problem, sum + 1))
                assertFalse("Oracle must reject sum - 1", gen.validateAnswer(problem, sum - 1))
                assertFalse("Oracle must reject sum + 1000", gen.validateAnswer(problem, sum + 1000))
                assertFalse("Oracle must reject 0", gen.validateAnswer(problem, 0))
                assertFalse("Oracle must reject negative sum", gen.validateAnswer(problem, -sum))

                // Stats collection
                if (a < minA) minA = a
                if (a > maxA) maxA = a
                if (b < minB) minB = b
                if (b > maxB) maxB = b
                if (sum < minSum) minSum = sum
                if (sum > maxSum) maxSum = sum

                uniqueA.add(a)
                uniqueB.add(b)
            }
        }

        // Verify boundary limits reached or closely approached
        assertEquals("Minimum operandA observed must be 1000", 1000, minA)
        assertEquals("Maximum operandA observed must be 9999", 9999, maxA)
        assertEquals("Minimum operandB observed must be 1000", 1000, minB)
        assertEquals("Maximum operandB observed must be 9999", 9999, maxB)
        assertTrue("Minimum sum observed ($minSum) must be <= 2500", minSum <= 2500)
        assertTrue("Maximum sum observed ($maxSum) must be >= 19500", maxSum >= 19500)

        // Verify high entropy across 9000 possible 4-digit numbers
        assertTrue("Hard unique operandA count (${uniqueA.size}) should be > 8900 out of 9000 possible", uniqueA.size > 8900)
        assertTrue("Hard unique operandB count (${uniqueB.size}) should be > 8900 out of 9000 possible", uniqueB.size > 8900)

        println("HARD STRESS TEST PASSED: $totalTested problems verified with 100% invariant adherence and high entropy (${uniqueA.size} unique A, ${uniqueB.size} unique B).")
    }

    // =========================================================================
    // 4. ADVERSARIAL ORACLE ATTACK VECTORS
    // =========================================================================

    @Test
    fun testAdversarialOracle_tamperingAndAdversarialInputs() {
        val generator = MathProblemGenerator.INSTANCE

        // Attack 1: Inconsistent MathProblem where problem.solution is forged / corrupted
        val forgedProblem1 = MathProblem(operandA = 10, operandB = 20, solution = 999)
        assertFalse(
            "Oracle MUST NOT accept forged solution even if answer matches problem.solution",
            generator.validateAnswer(forgedProblem1, 999)
        )
        assertFalse(
            "Oracle MUST NOT accept actual sum if problem.solution is forged",
            generator.validateAnswer(forgedProblem1, 30)
        )

        // Attack 2: Integer overflow attempts
        val overflowProblem = MathProblem(operandA = 9999, operandB = 9999, solution = 19998)
        assertFalse(generator.validateAnswer(overflowProblem, Int.MAX_VALUE))
        assertFalse(generator.validateAnswer(overflowProblem, Int.MIN_VALUE))
        assertFalse(generator.validateAnswer(overflowProblem, 19998 + Int.MAX_VALUE)) // overflow wrap

        // Attack 3: Digit concatenation vs addition
        // 3 + 4 = 7, but user types "34"
        val concatProblem = MathProblem(operandA = 3, operandB = 4, solution = 7)
        assertFalse(generator.validateAnswer(concatProblem, 34))

        // Attack 4: Subtraction instead of addition
        // 5 + 2 = 7, but user computes 5 - 2 = 3
        val subProblem = MathProblem(operandA = 5, operandB = 2, solution = 7)
        assertFalse(generator.validateAnswer(subProblem, 3))

        // Attack 5: Multiplication instead of addition
        // 4 + 4 = 8, but user computes 4 * 4 = 16
        val multProblem = MathProblem(operandA = 4, operandB = 4, solution = 8)
        assertFalse(generator.validateAnswer(multProblem, 16))

        println("ORACLE ADVERSARIAL ATTACKS PASSED: Tampered states and arithmetic attack vectors successfully blocked.")
    }

    // =========================================================================
    // 5. CONCURRENT MULTI-THREADED CONTENTION TEST
    // =========================================================================

    @Test
    fun testConcurrentContention_50kProblemsAcrossMultipleThreads() {
        val threadCount = 10
        val iterationsPerThread = 5_000
        val latch = CountDownLatch(threadCount)
        val failureQueue = ConcurrentLinkedQueue<String>()
        val totalProcessed = AtomicInteger(0)

        val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)

        for (t in 0 until threadCount) {
            Thread {
                try {
                    for (i in 0 until iterationsPerThread) {
                        val difficulty = difficulties[i % difficulties.size]
                        val problem = MathProblemGenerator.INSTANCE.generateProblem(difficulty)
                        totalProcessed.incrementAndGet()

                        val a = problem.operandA
                        val b = problem.operandB
                        val sum = problem.solution

                        when (difficulty) {
                            Difficulty.EASY -> {
                                if (a !in 1..9 || b !in 1..9 || sum != a + b) {
                                    failureQueue.add("Easy invariant violated: $a + $b = $sum")
                                }
                            }
                            Difficulty.MEDIUM -> {
                                if (a !in 10..99 || b !in 10..99 || sum != a + b) {
                                    failureQueue.add("Medium invariant violated: $a + $b = $sum")
                                }
                            }
                            Difficulty.HARD -> {
                                if (a !in 1000..9999 || b !in 1000..9999 || sum != a + b) {
                                    failureQueue.add("Hard invariant violated: $a + $b = $sum")
                                }
                            }
                        }

                        if (!MathProblemGenerator.INSTANCE.validateAnswer(problem, sum)) {
                            failureQueue.add("Validation failed for valid problem $a + $b = $sum")
                        }
                    }
                } catch (e: Throwable) {
                    failureQueue.add("Exception in thread $t: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        val completed = latch.await(30, TimeUnit.SECONDS)
        assertTrue("Concurrent execution timed out", completed)
        assertTrue(
            "Concurrent execution encountered ${failureQueue.size} failures: ${failureQueue.take(5)}",
            failureQueue.isEmpty()
        )
        assertEquals(50_000, totalProcessed.get())

        println("CONCURRENCY TEST PASSED: 50,000 problems processed concurrently across 10 threads without failure.")
    }
}
