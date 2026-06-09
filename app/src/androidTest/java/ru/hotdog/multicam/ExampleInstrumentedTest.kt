package ru.hotdog.multicam

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

// Проверяет пример инструментального теста на Android-устройстве.
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    // Проверяет packageName контекста тестируемого приложения.
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.multicam", appContext.packageName)
    }
}