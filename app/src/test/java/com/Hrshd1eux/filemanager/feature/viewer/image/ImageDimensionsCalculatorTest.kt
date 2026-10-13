package com.Hrshd1eux.filemanager.feature.viewer.image

import com.Hrshd1eux.filemanager.feature.viewer.image.model.ImageDimensions
import com.Hrshd1eux.filemanager.feature.viewer.image.util.ImageDimensionsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageDimensionsCalculatorTest {

    @Test
    fun calculateInSampleSize_exactOrSmallerReturnsOne() {
        val sampleSize = ImageDimensionsCalculator.calculateInSampleSize(
            rawWidth = 1080,
            rawHeight = 1920,
            reqWidth = 1080,
            reqHeight = 1920
        )
        assertEquals(1, sampleSize)

        val smallerSampleSize = ImageDimensionsCalculator.calculateInSampleSize(
            rawWidth = 800,
            rawHeight = 600,
            reqWidth = 1920,
            reqHeight = 1080
        )
        assertEquals(1, smallerSampleSize)
    }

    @Test
    fun calculateInSampleSize_largeImagesDownsamplePowersOfTwo() {
        // 4K image downsampled to 1080p
        val sample4k = ImageDimensionsCalculator.calculateInSampleSize(
            rawWidth = 3840,
            rawHeight = 2160,
            reqWidth = 1920,
            reqHeight = 1080
        )
        assertEquals(2, sample4k)

        // 8K image downsampled to 1080p
        val sample8k = ImageDimensionsCalculator.calculateInSampleSize(
            rawWidth = 7680,
            rawHeight = 4320,
            reqWidth = 1920,
            reqHeight = 1080
        )
        assertEquals(4, sample8k)
    }

    @Test
    fun calculateInSampleSizeForMaxDimension_boundsMaxDimension() {
        // 108MP phone camera image (12000 x 9000) constrained to max 2048px
        val sample108mp = ImageDimensionsCalculator.calculateInSampleSizeForMaxDimension(
            rawWidth = 12000,
            rawHeight = 9000,
            maxDimension = 2048
        )
        // 12000 / 8 = 1500 <= 2048
        assertEquals(8, sample108mp)
        val decodedMax = 12000 / sample108mp
        assertTrue(decodedMax <= 2048)
    }

    @Test
    fun calculateEffectiveDimensions_swapsOn90And270Degrees() {
        // Landscape original, 90° rotated -> visual portrait
        val (w90, h90) = ImageDimensionsCalculator.calculateEffectiveDimensions(1920, 1080, 90)
        assertEquals(1080, w90)
        assertEquals(1920, h90)

        // Landscape original, 270° rotated -> visual portrait
        val (w270, h270) = ImageDimensionsCalculator.calculateEffectiveDimensions(1920, 1080, 270)
        assertEquals(1080, w270)
        assertEquals(1920, h270)

        // 0° and 180° retain original aspect orientation
        val (w0, h0) = ImageDimensionsCalculator.calculateEffectiveDimensions(1920, 1080, 0)
        assertEquals(1920, w0)
        assertEquals(1080, h0)

        val (w180, h180) = ImageDimensionsCalculator.calculateEffectiveDimensions(1920, 1080, 180)
        assertEquals(1920, w180)
        assertEquals(1080, h180)
    }

    @Test
    fun estimateMemoryFootprintBytes_demonstratesSubsamplingProtection() {
        // Raw 12000x9000 uncompressed ARGB_8888 bitmap: ~432 MB!
        val rawRamBytes = ImageDimensionsCalculator.estimateMemoryFootprintBytes(
            width = 12000,
            height = 9000,
            inSampleSize = 1,
            bytesPerPixel = 4
        )
        assertEquals(432_000_000L, rawRamBytes)

        // Subsampled by factor 8 with RGB_565 (2 bytes/pixel): ~3.37 MB!
        val safeRamBytes = ImageDimensionsCalculator.estimateMemoryFootprintBytes(
            width = 12000,
            height = 9000,
            inSampleSize = 8,
            bytesPerPixel = 2
        )
        assertEquals(1500L * 1125L * 2L, safeRamBytes)
        assertTrue(safeRamBytes < 4_000_000L) // Under 4MB!
    }

    @Test
    fun exifOrientationToDegrees_mapsTagsAccurately() {
        assertEquals(90, ImageDimensionsCalculator.exifOrientationToDegrees(6))
        assertEquals(180, ImageDimensionsCalculator.exifOrientationToDegrees(3))
        assertEquals(270, ImageDimensionsCalculator.exifOrientationToDegrees(8))
        assertEquals(0, ImageDimensionsCalculator.exifOrientationToDegrees(1))
        assertEquals(0, ImageDimensionsCalculator.exifOrientationToDegrees(0))
    }

    @Test
    fun imageDimensionsModel_visualDimensionsAndFormatting() {
        val normal = ImageDimensions(width = 4032, height = 3024, orientationDegrees = 0)
        assertEquals(4032, normal.visualWidth)
        assertEquals(3024, normal.visualHeight)
        assertEquals("4032 × 3024", normal.formatted)
        assertTrue(normal.isValid)

        val rotated = ImageDimensions(width = 4032, height = 3024, orientationDegrees = 90)
        assertEquals(3024, rotated.visualWidth)
        assertEquals(4032, rotated.visualHeight)
        assertEquals("3024 × 4032", rotated.formatted)

        val invalid = ImageDimensions(width = 0, height = 0)
        assertFalse(invalid.isValid)
        assertEquals("Unknown", invalid.formatted)
    }
}
