# Copyright (C) 2011 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)

# test dex library
# ============================================================
include $(CLEAR_VARS)

# custom variables used to generate test description. do not touch!
LOCAL_SRC_FILES := $(call all-java-files-under, src/dot)

LOCAL_MODULE := cts-tf-dalvik-lib
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE_TAGS := optional
LOCAL_JAVA_LIBRARIES := junit-targetdex

include $(BUILD_JAVA_LIBRARY)

cts-tf-dalvik-lib.jack := $(full_classes_jack)

# buildutil java library
# ============================================================
include $(CLEAR_VARS)

# custom variables used to generate test description. do not touch!
LOCAL_TEST_TYPE := vmHostTest
LOCAL_JAR_PATH := android.core.vm-tests-tf.jar

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_MODULE := cts-tf-dalvik-buildutil
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE_TAGS := optional

LOCAL_JAVA_LIBRARIES := dx dasm cfassembler junit jsr305lib

LOCAL_CLASSPATH := $(HOST_JDK_TOOLS_JAR)

include $(BUILD_HOST_JAVA_LIBRARY)

# Buid android.core.vm-tests-tf.jar
# ============================================================
#
include $(CLEAR_VARS)

intermediates := $(call intermediates-dir-for,JAVA_LIBRARIES,vm-tests-tf,HOST)
vmteststf_jar := $(intermediates)/android.core.vm-tests-tf.jar
vmteststf_dep_jars := $(addprefix $(HOST_OUT_JAVA_LIBRARIES)/, cts-tf-dalvik-buildutil.jar dasm.jar dx.jar cfassembler.jar junit.jar)

vmteststf_dep_jars += $(cts-tf-dalvik-lib.jack)

$(vmteststf_jar): PRIVATE_SRC_FOLDER := $(LOCAL_PATH)/src
$(vmteststf_jar): PRIVATE_INTERMEDIATES_CLASSES := $(call intermediates-dir-for,JAVA_LIBRARIES,cts-tf-dalvik-buildutil,HOST)/classes
$(vmteststf_jar): PRIVATE_INTERMEDIATES := $(intermediates)/tests
$(vmteststf_jar): PRIVATE_INTERMEDIATES_DEXCORE_JAR := $(intermediates)/tests/dot/junit/dexcore.jar
$(vmteststf_jar): PRIVATE_INTERMEDIATES_MAIN_FILES := $(intermediates)/main_files
$(vmteststf_jar): PRIVATE_INTERMEDIATES_HOSTJUNIT_FILES := $(intermediates)/hostjunit_files
$(vmteststf_jar): PRIVATE_CLASS_PATH := $(subst $(space),:,$(vmteststf_dep_jars)):$(HOST_JDK_TOOLS_JAR)
$(vmteststf_jar): PRIVATE_JACK_VERSION := $(LOCAL_JACK_VERSION)
oj_jack := $(call intermediates-dir-for,JAVA_LIBRARIES,core-oj,,COMMON)/classes.jack
libart_jack := $(call intermediates-dir-for,JAVA_LIBRARIES,core-libart,,COMMON)/classes.jack
$(vmteststf_jar): PRIVATE_DALVIK_SUITE_CLASSPATH := $(oj_jack):$(libart_jack):$(cts-tf-dalvik-lib.jack):$(HOST_OUT_JAVA_LIBRARIES)/tradefed-prebuilt.jar
$(vmteststf_jar) : $(vmteststf_dep_jars) $(JACK) $(oj_jack) $(libart_jack) $(HOST_OUT_JAVA_LIBRARIES)/tradefed-prebuilt.jar | setup-jack-server
	$(hide) rm -rf $(dir $@) && mkdir -p $(dir $@)
	$(hide) mkdir -p $(PRIVATE_INTERMEDIATES_HOSTJUNIT_FILES)/dot/junit $(dir $(PRIVATE_INTERMEDIATES_DEXCORE_JAR))
	# generated and compile the host side junit tests
	@echo "Write generated Main_*.java files to $(PRIVATE_INTERMEDIATES_MAIN_FILES)"
	$(hide) JACK_VERSION=$(PRIVATE_JACK_VERSION) java -cp $(PRIVATE_CLASS_PATH) util.build.JackBuildDalvikSuite $(JACK) $(PRIVATE_SRC_FOLDER) $(PRIVATE_INTERMEDIATES) \
		$(PRIVATE_DALVIK_SUITE_CLASSPATH) \
		$(PRIVATE_INTERMEDIATES_MAIN_FILES) $(PRIVATE_INTERMEDIATES_CLASSES) $(PRIVATE_INTERMEDIATES_HOSTJUNIT_FILES) $$RUN_VM_TESTS_RTO
	@echo "Generate $(PRIVATE_INTERMEDIATES_DEXCORE_JAR)"
	$(hide) jar -cf $(PRIVATE_INTERMEDIATES_DEXCORE_JAR)-class.jar \
		$(addprefix -C $(PRIVATE_INTERMEDIATES_CLASSES) , dot/junit/DxUtil.class dot/junit/DxAbstractMain.class)
	$(hide) $(call call-jack) --import $(PRIVATE_INTERMEDIATES_DEXCORE_JAR)-class.jar --output-jack $(PRIVATE_INTERMEDIATES_DEXCORE_JAR).jack
	$(hide) mkdir -p $(PRIVATE_INTERMEDIATES_DEXCORE_JAR).tmp
	$(hide) $(call call-jack,$(PRIVATE_JACK_EXTRA_ARGS)) --output-dex $(PRIVATE_INTERMEDIATES_DEXCORE_JAR).tmp \
		$(if $(NO_OPTIMIZE_DX), -D jack.dex.optimize "false") --import $(PRIVATE_INTERMEDIATES_DEXCORE_JAR).jack && rm -f $(PRIVATE_INTERMEDIATES_DEXCORE_JAR).jack
	$(hide) cd $(PRIVATE_INTERMEDIATES_DEXCORE_JAR).tmp && zip -q -r $(abspath $(PRIVATE_INTERMEDIATES_DEXCORE_JAR)) .
	$(hide) cd $(PRIVATE_INTERMEDIATES_HOSTJUNIT_FILES)/classes && zip -q -r ../../android.core.vm-tests-tf.jar .
	$(hide) cd $(dir $@) && zip -q -r android.core.vm-tests-tf.jar tests
oj_jack :=
libart_jack :=

# Clean up temp vars
intermediates :=
vmteststf_jar :=
vmteststf_dep_jars :=
