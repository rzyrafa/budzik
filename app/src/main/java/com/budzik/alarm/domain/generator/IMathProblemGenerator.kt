package com.budzik.alarm.domain.generator

import com.budzik.alarm.domain.model.Difficulty
import com.budzik.alarm.domain.model.MathProblem

interface IMathProblemGenerator {
    fun generateProblem(difficulty: Difficulty): MathProblem
    fun validateAnswer(problem: MathProblem, answer: Int): Boolean
}
