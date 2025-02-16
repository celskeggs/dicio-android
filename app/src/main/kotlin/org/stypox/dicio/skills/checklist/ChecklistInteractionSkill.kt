package org.stypox.dicio.skills.checklist

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.ChecklistOk
import org.stypox.dicio.skills.checklist.ChecklistInfo.checklistDataStore
import org.stypox.dicio.skills.checklist.ChecklistSkill.Companion.timestampNow
import org.stypox.dicio.skills.checklist.ChecklistSkill.Companion.timestampToLocal
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class ChecklistInteractionSkill(
    correspondingSkillInfo: SkillInfo,
    private val checklistIndex: Int,
    data: StandardRecognizerData<ChecklistOk>,
) : StandardRecognizerSkill<ChecklistOk>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: ChecklistOk): SkillOutput {
        var output: SkillOutput? = null
        ctx.android.checklistDataStore.updateData {
            it.copy {
                val checklist = checklists[checklistIndex]
                when (inputData) {
                    is ChecklistOk.Complete -> {
                        val pair = advanceState(checklistIndex, markCurrentItem(checklist, ItemState.COMPLETED), false)
                        output = pair.first
                        checklists[checklistIndex] = pair.second
                    }

                    is ChecklistOk.Skip -> {
                        val pair = advanceState(checklistIndex, markCurrentItem(checklist, ItemState.SKIPPED), false)
                        output = pair.first
                        checklists[checklistIndex] = pair.second
                    }

                    is ChecklistOk.Query -> {
                        val pair = advanceState(checklistIndex, checklist, false)
                        output = pair.first
                        checklists[checklistIndex] = pair.second
                    }

                    is ChecklistOk.Wait -> output = ChecklistSkillOutput("Okay!", checklistIndex, false)

                    is ChecklistOk.Abort -> output = object :
                        ChecklistSkillOutput("Okay, that's all for now.", null, false) {
                        override fun getNextSkills(ctx: SkillContext): List<Skill<*>> = listOf()
                    }

                    is ChecklistOk.Reset -> {
                        val pair = startChecklist(ctx, checklistIndex, checklist.copy { executionState = ChecklistState.NOT_STARTED }, "Okay, let's start from the beginning. ")
                        output = pair.first
                        checklists[checklistIndex] = pair.second
                    }
                }
            }
        }
        return output ?: ChecklistSkillOutput("Internal error.", null)
    }

    companion object {
        fun markCurrentItem(checklist: Checklist, newState: ItemState): Checklist =
            checklist.copy {
                if (executionLastIndex < checklistItem.size && checklistItem[executionLastIndex].executionState != newState) {
                    checklistItem[executionLastIndex] = checklistItem[executionLastIndex].copy {
                        executionState = newState
                        executionLastChanged = timestampNow()
                    }
                    executionLastIndex += 1
                }
            }

        fun advanceState(
            checklistIndex: Int,
            checklistValue: Checklist,
            verbose: Boolean
        ): Pair<ChecklistSkillOutput, Checklist> {
            var checklist = checklistValue
            val now = timestampNow()
            if (checklist.executionState != ChecklistState.IN_PROGRESS) {
                checklist = checklist.copy {
                    executionStartedAt = now
                    executionState = ChecklistState.IN_PROGRESS
                    executionLastIndex = 0
                    checklistItem.forEachIndexed { index, item ->
                        checklistItem[index] = item.copy {
                            executionState = ItemState.NOT_ASKED
                            executionLastChanged = now
                        }
                    }
                }
            }
            // Fast forward past any items already completed.
            var currentIndex = checklist.executionLastIndex
            while (currentIndex < checklist.checklistItemCount && checklist.checklistItemList[currentIndex].executionState == ItemState.COMPLETED) {
                currentIndex += 1
            }
            if (currentIndex >= checklist.checklistItemCount) {
                // Did we skip anything earlier?
                var newIndex: Int = currentIndex
                checklist.checklistItemList.forEachIndexed { index, item ->
                    if (item.executionState != ItemState.COMPLETED && newIndex == currentIndex) {
                        newIndex = index
                    }
                }
                if (newIndex == currentIndex) {
                    checklist = checklist.copy {
                        executionEndedAt = now
                        executionLastIndex = currentIndex
                        executionState = ChecklistState.COMPLETE
                    }
                    val elapsedTime = Duration.between(ChecklistSkill.timestampToInstant(checklist.executionStartedAt), ChecklistSkill.timestampToInstant(now))
                    return Pair(ChecklistSkillOutput("Checklist complete. Time elapsed: ${renderDuration(elapsedTime)}.", null, false), checklist)
                } else {
                    checklist = checklist.copy {
                        executionLastIndex = newIndex
                        checklistItem[newIndex] = checklistItem[newIndex].copy {
                            executionState = ItemState.ASKED
                            executionLastChanged = now
                        }
                    }
                    val item = checklist.checklistItemList[newIndex]
                    return Pair(ChecklistSkillOutput("Let's circle back to item ${newIndex + 1}. ${item.itemName.ifBlank{"There's no description for it."}}", checklistIndex), checklist)
                }
            } else {
                checklist = checklist.copy {
                    executionLastIndex = currentIndex
                    checklistItem[currentIndex] = checklistItem[currentIndex].copy {
                        executionState = ItemState.ASKED
                        executionLastChanged = now
                    }
                }
            }
            val item = checklist.checklistItemList[checklist.executionLastIndex]
            return Pair(
                ChecklistSkillOutput(if (verbose) {
                    "Item ${currentIndex + 1}. ${item.itemName.ifBlank{"There's no description for it."}}"
                } else {
                    item.itemName.ifBlank { "Next is Item ${currentIndex + 1}. There's no description for it." }
                }, checklistIndex),
                checklist
            )
        }

        fun startChecklist(
            ctx: SkillContext,
            checklistIndex: Int,
            checklist: Checklist,
            intro: String = ""
        ): Pair<SkillOutput, Checklist> {
            val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(ctx.locale)

            val title = checklist.checklistName.ifEmpty { "Unspecified" }
            var outputText = intro.ifEmpty {
                when (checklist.executionState) {
                    ChecklistState.IN_PROGRESS -> "Let's continue with the checklist for $title, which you started at ${
                        formatter.format(
                            timestampToLocal(checklist.executionStartedAt)
                        )
                    }. "
                    ChecklistState.COMPLETE -> "I'll restart the checklist for $title, which you last completed at ${
                        formatter.format(
                            timestampToLocal(checklist.executionEndedAt)
                        )
                    }. "
                    else -> "I'll start the checklist for $title. "
                }
            }
            outputText += if (checklist.checklistItemCount == 1) {
                "There is 1 item in this checklist. "
            } else {
                "There are ${checklist.checklistItemCount} items in this checklist. "
            }
            val pair = advanceState(checklistIndex, checklist, true)
            return Pair(pair.first.updateText { outputText + it }, pair.second)
        }

        private fun renderDuration(elapsedTime: Duration): String =
            if (elapsedTime.toDays() > 0) {
                "${elapsedTime.toDays()} days ${elapsedTime.toHours() % 24} hours"
            } else if (elapsedTime.toHours() > 0) {
                "${elapsedTime.toHours()} hours ${elapsedTime.toMinutes() % 24} minutes"
            } else if (elapsedTime.toMinutes() > 0) {
                "${elapsedTime.toMinutes()} minutes ${elapsedTime.toSeconds() % 60} seconds"
            } else {
                "${elapsedTime.toSeconds()} seconds"
            }
    }

}
