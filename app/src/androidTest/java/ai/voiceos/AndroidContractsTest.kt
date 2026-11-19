package ai.voiceos

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.voiceos.actions.AndroidActions
import ai.voiceos.actions.copyText
import ai.voiceos.core.ActionFailure
import ai.voiceos.core.ActionResult
import ai.voiceos.core.CommandResult
import ai.voiceos.core.DeviceAction
import ai.voiceos.core.Interpretation
import ai.voiceos.core.LocalInterpreter
import ai.voiceos.data.HistoryDatabase
import ai.voiceos.data.InputSource
import ai.voiceos.data.StoredAction
import ai.voiceos.settings.VoiceLanguage
import ai.voiceos.settings.localized
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidContractsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun advertisedCommandsMatchBothLocales() {
        val interpreter = LocalInterpreter()
        for (language in VoiceLanguage.entries) for (action in DeviceAction.entries) {
            val phrase = context.localized(language).getString(action.copyText().phrase)
            assertEquals(phrase, Interpretation.Matched(action), interpreter.interpret(phrase))
        }
    }

    @Test fun actionAdapterRejectsMissingForegroundHost() = runBlocking {
        val result = AndroidActions().execute(DeviceAction.CAMERA)
        assertEquals(ActionResult.Failed(ActionFailure.NOT_FOREGROUND), result)
    }

    @Test fun roomKeepsBoundedMetadataAndClearsIt() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java).build()
        try {
            val dao = db.history()
            val result = CommandResult.Executed(DeviceAction.BATTERY, ActionResult.Battery(60))
            repeat(StoredAction.LIMIT + 5) { i ->
                dao.record(StoredAction.from("test-$i", i.toLong(), InputSource.TEXT, result))
            }
            val records = dao.observe().first()
            assertEquals(StoredAction.LIMIT, records.size)
            assertEquals(StoredAction.LIMIT + 4L, records.first().occurredAt)
            assertEquals(5L, records.last().occurredAt)
            assertEquals(result, records.first().toResult())
            dao.clear()
            assertTrue(dao.observe().first().isEmpty())
        } finally {
            db.close()
        }
    }
}
