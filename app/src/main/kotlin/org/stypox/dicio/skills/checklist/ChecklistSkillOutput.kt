package org.stypox.dicio.skills.checklist

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.sentences.Sentences

/**
 * A [SkillOutput] where the graphical output is just a headline text with the speech output.
 */
open class ChecklistSkillOutput(private val literal: String, private val checklistIndex: Int?, private val keepListening: Boolean = true) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = literal

    override fun getNextSkills(ctx: SkillContext): List<Skill<*>> = if (checklistIndex != null) {
        listOf(
            ChecklistInteractionSkill(ChecklistInfo, checklistIndex, Sentences.ChecklistOk[ctx.sentencesLanguage]!!)
        )
    } else {
        listOf()
    }

    override fun getKeepListening(ctx: SkillContext): Boolean = keepListening

    fun updateText(update: (String) -> String): ChecklistSkillOutput =
        ChecklistSkillOutput(update(literal), checklistIndex, keepListening)
}
