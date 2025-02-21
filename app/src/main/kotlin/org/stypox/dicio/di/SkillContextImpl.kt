package org.stypox.dicio.di

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.dicio.numbers.ParserFormatter
import org.dicio.skill.context.NoSettingsAccess
import org.dicio.skill.context.SettingsAccess
import org.dicio.skill.context.SkillContext
import org.dicio.skill.context.SpeechOutputDevice
import org.dicio.skill.skill.InteractionLog
import org.dicio.skill.skill.Permission
import org.stypox.dicio.eval.SkillEvaluator
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.speech.NothingSpeechDevice
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillContextImpl private constructor(
    override val android: Context,
    private val localeManager: LocaleManager,
    // this constructor can take any SpeechOutputDevice to allow newForPreviews to provide
    // NothingSpeechDevice
    override val speechOutputDevice: SpeechOutputDevice,
    override val settingsAccess: SettingsAccess,
    private val skillEvaluator: Lazy<SkillEvaluator>
) : SkillContext {

    @Inject
    constructor(
        @ApplicationContext android: Context,
        localeManager: LocaleManager,
        speechOutputDevice: SpeechOutputDeviceWrapper,
        settingsAccess: SettingsAccessWrapper,
        skillEvaluator: Lazy<SkillEvaluator>,
    ) : this(android, localeManager, speechOutputDevice as SpeechOutputDevice, settingsAccess as SettingsAccess, { skillEvaluator.get() })


    override val locale: Locale
        get() = localeManager.locale.value

    override val sentencesLanguage: String
        get() = localeManager.sentencesLanguage.value

    private var lastParserFormatter: Pair<ParserFormatter?, Locale>? = null
    override val parserFormatter: ParserFormatter?
        get() {
            val currentLocale = locale

            if (lastParserFormatter?.second?.equals(currentLocale) != true) {
                lastParserFormatter = try {
                    Pair(ParserFormatter(currentLocale), currentLocale)
                } catch (ignored: IllegalArgumentException) {
                    // current locale is not supported by dicio-numbers
                    Pair(null, currentLocale)
                }
            }

            return lastParserFormatter?.first
        }

    override val interactionLog: InteractionLog
        get() = skillEvaluator.get().state.value

    companion object {
        fun newForPreviews(context: Context): SkillContextImpl {
            val localeManager = LocaleManager.newForPreviews(context)
            val res = SkillContextImpl(
                context,
                localeManager,
                NothingSpeechDevice(),
                NoSettingsAccess(),
                { object : SkillEvaluator {
                    override val state: StateFlow<InteractionLog>
                        get() = MutableStateFlow(InteractionLog(listOf(), null))

                    override var permissionRequester: suspend (List<Permission>) -> Boolean
                        get() = { false }
                        set(_) {}

                    override fun processInputEvent(event: InputEvent) {}
                } }
            )
            @SuppressLint("StateFlowValueCalledInComposition")
            res.lastParserFormatter = Pair(
                null,
                localeManager.locale.value
            )
            return res
        }
    }
}
