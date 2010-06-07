 /*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.apis.app

import _root_.android.app.Activity
import _root_.android.os.Bundle
import _root_.android.speech.tts.TextToSpeech
import _root_.android.util.Log
import _root_.android.view.View
import _root_.android.widget.Button

import com.example.android.apis.R

import java.util.Locale
import scala.util.Random

/**
 * <p>Demonstrates text-to-speech (TTS). Please note the following steps:</p>
 *
 * <ol>
 * <li>Construct the TextToSpeech object.</li>
 * <li>Handle initialization callback in the onInit method.
 * The activity implements TextToSpeech.OnInitListener for this purpose.</li>
 * <li>Call TextToSpeech.speak to synthesize speech.</li>
 * <li>Shutdown TextToSpeech in onDestroy.</li>
 * </ol>
 *
 * <p>Documentation:
 * http://developer.android.com/reference/android/speech/tts/package-summary.html
 * </p>
 * <ul>
 */
object TextToSpeechActivity {
  private final val TAG = "TextToSpeechDemo"

  private final val RANDOM = new Random
  private final val HELLOS = Array(
    "Hello",
    "Salutations",
    "Greetings",
    "Howdy",
    "What's crack-a-lackin?",
    "That explains the stench!")
}

class TextToSpeechActivity extends Activity
                              with TextToSpeech.OnInitListener {
  import TextToSpeechActivity._  // companion object

  private var mTts: TextToSpeech = _
  private var mAgainButton: Button = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.text_to_speech)

    // Initialize text-to-speech. This is an asynchronous operation.
    // The OnInitListener (second argument) is called after initialization completes.
    mTts = new TextToSpeech(this,
            this  // TextToSpeech.OnInitListener
            )

    // The button is disabled in the layout.
    // It will be enabled upon initialization of the TTS engine.
    mAgainButton = findViewById(R.id.again_button).asInstanceOf[Button]

    mAgainButton setOnClickListener new View.OnClickListener {
      def onClick(v: View) {
         sayHello()
      }
    }
  }

  override def onDestroy() {
    // Don't forget to shutdown!
    if (mTts != null) {
      mTts.stop()
      mTts.shutdown()
    }
    super.onDestroy()
  }

  // Implements TextToSpeech.OnInitListener.
  def onInit(status: Int) {
    // status can be either TextToSpeech.SUCCESS or TextToSpeech.ERROR.
    if (status == TextToSpeech.SUCCESS) {
      // Set preferred language to US english.
      // Note that a language may not be available, and the result will indicate this.
      val result = mTts setLanguage Locale.US
      // Try this someday for some interesting results.
      // val result mTts setLanguage Locale.FRANCE
      if (result == TextToSpeech.LANG_MISSING_DATA ||
          result == TextToSpeech.LANG_NOT_SUPPORTED) {
        // Lanuage data is missing or the language is not supported.
        Log.e(TAG, "Language is not available.")
      } else {
        // Check the documentation for other possible result codes.
        // For example, the language may be available for the locale,
        // but not for the specified country and variant.

        // The TTS engine has been successfully initialized.
        // Allow the user to press the button for the app to speak again.
        mAgainButton setEnabled true
        // Greet the user.
        sayHello()
      }
    } else {
      // Initialization failed.
      Log.e(TAG, "Could not initialize TextToSpeech.")
    }
  }

  private def sayHello() {
    // Select a random hello.
    val helloLength = HELLOS.length
    val hello = HELLOS(RANDOM nextInt helloLength)
    mTts.speak(hello,
               TextToSpeech.QUEUE_FLUSH,  // Drop all pending entries in the playback queue.
               null)
  }

}