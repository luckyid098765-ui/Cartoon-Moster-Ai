package com.example

import com.example.ai.CartoonAiGenerator
import com.example.model.CartoonLanguage
import com.example.model.CartoonStyle
import com.example.model.ProjectStatus
import com.example.model.VideoDurationOption
import com.example.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun videoQualitiesInclude480p720pAnd1080p() {
    assertEquals(854, VideoQuality.SD_480P.width)
    assertEquals(480, VideoQuality.SD_480P.height)
    assertEquals(1280, VideoQuality.HD_720P.width)
    assertEquals(720, VideoQuality.HD_720P.height)
    assertEquals(1920, VideoQuality.FHD_1080P.width)
    assertEquals(1080, VideoQuality.FHD_1080P.height)
  }

  @Test
  fun cartoonStylesSupportRequiredModes() {
    val names = CartoonStyle.entries.map { it.name }.toSet()
    assertTrue("THREE_D" in names)
    assertTrue("TWO_D" in names)
    assertTrue("REALISTIC_CARTOON" in names)
    assertTrue("CINEMATIC" in names)
  }

  @Test
  fun maxDurationIs30MinutesAndLanguagesCoverRequiredSet() {
    assertEquals(30, VideoDurationOption.entries.maxOf { it.minutes })
    assertEquals(4, CartoonAiGenerator.targetSceneCountFor(VideoDurationOption.MIN_1))
    assertEquals(20, CartoonAiGenerator.targetSceneCountFor(VideoDurationOption.MIN_30))
    assertEquals(setOf("ENGLISH", "HINDI", "BENGALI"), CartoonLanguage.entries.map { it.name }.toSet())
  }

  @Test
  fun pausedAndFailedAreNotRunningOrCompleted() {
    assertFalse(ProjectStatus.PAUSED.isRunning)
    assertFalse(ProjectStatus.FAILED.isRunning)
    assertFalse(ProjectStatus.COMPLETED.isRunning)
    assertTrue(ProjectStatus.RENDERING_VIDEO.isRunning)
  }
}
