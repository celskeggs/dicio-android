package org.stypox.dicio.skills.checklist

import com.google.protobuf.Timestamp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.sentences.Sentences.Checklist
import org.stypox.dicio.skills.checklist.ChecklistInfo.checklistDataStore
import org.stypox.dicio.util.RecognizeYesNoSkill
import org.stypox.dicio.util.StringUtils
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ChecklistSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Checklist>) :
    StandardRecognizerSkill<Checklist>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: Checklist): SkillOutput {
        var output: SkillOutput? = null
        ctx.android.checklistDataStore.updateData {
            it.copy {
                when (inputData) {
                    is Checklist.Start -> {
                        if (checklists.isEmpty()) {
                            output = ChecklistSkillOutput("You haven't defined any checklists.", null, false)
                        } else {
                            val checklistIndex: Int? = if (inputData.list.isNullOrBlank()) {
                                if (executionLastChecklistIndex < checklists.size) {
                                    executionLastChecklistIndex
                                } else {
                                    output = ChecklistSkillOutput("I don't know which checklist you want.", null, false)
                                    null
                                }
                            } else {
                                val (checklistIndex, distance) = findChecklistByName(it, inputData.list)
                                if (distance < 0) {
                                    checklistIndex
                                } else {
                                    output = object : ChecklistSkillOutput("Do you want to start the ${checklists[checklistIndex].checklistName} checklist?", null) {
                                        override fun getNextSkills(ctx: SkillContext): List<Skill<*>> = listOf(
                                            object : RecognizeYesNoSkill(ChecklistInfo, Sentences.UtilYesNo[ctx.sentencesLanguage]!!) {
                                                override suspend fun generateOutput(ctx: SkillContext, inputData: Boolean): SkillOutput =
                                                    if (inputData) {
                                                        var innerOutput: SkillOutput? = null
                                                        ctx.android.checklistDataStore.updateData { innerIt ->
                                                            innerIt.copy {
                                                                val pair = ChecklistInteractionSkill.startChecklist(ctx, checklistIndex, checklists[checklistIndex])
                                                                innerOutput = pair.first
                                                                checklists[checklistIndex] = pair.second
                                                                executionLastChecklistIndex = checklistIndex
                                                            }
                                                        }
                                                        innerOutput ?: ChecklistSkillOutput("Internal error.", null)
                                                    } else {
                                                        ChecklistSkillOutput("Okay, I don't know which checklist you want.", null, false)
                                                    }
                                            }
                                        )
                                    }
                                    null
                                }
                            }
                            if (checklistIndex != null) {
                                val pair = ChecklistInteractionSkill.startChecklist(ctx, checklistIndex, checklists[checklistIndex])
                                output = pair.first
                                checklists[checklistIndex] = pair.second
                                executionLastChecklistIndex = checklistIndex
                            }
                        }
                    }
                }
            }
        }
        return output ?: ChecklistSkillOutput("Internal error.", null)
    }

    companion object {
        fun timestampToInstant(timestamp: com.google.protobuf.Timestamp): Instant =
            Instant.ofEpochSecond(timestamp.seconds, timestamp.nanos.toLong())

        fun timestampToLocal(timestamp: com.google.protobuf.Timestamp): LocalDateTime =
            LocalDateTime.ofInstant(timestampToInstant(timestamp), ZoneId.systemDefault())

        fun timestampNow(): com.google.protobuf.Timestamp {
            val now = Instant.now()
            return Timestamp.newBuilder().setSeconds(now.epochSecond).setNanos(now.nano).build()
        }

        fun findChecklistByName(checklists: SkillSettingsChecklist, name: String): Pair<Int, Int> =
            checklists.checklistsList.mapIndexedNotNull { index, checklist ->
                Pair(index, StringUtils.customStringDistance(name, checklist.checklistName))
            }
            .minByOrNull { pair -> pair.second }!!
    }
}
