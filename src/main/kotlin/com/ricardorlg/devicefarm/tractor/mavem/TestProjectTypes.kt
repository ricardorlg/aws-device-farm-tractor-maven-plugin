package com.ricardorlg.devicefarm.tractor.mavem

import software.amazon.awssdk.services.devicefarm.model.UploadType

enum class TestProjectTypes(private val awsType: UploadType) {
    ANDROID(UploadType.ANDROID_APP),
    IOS(UploadType.IOS_APP);

    fun toAwsUploadType() = awsType
}