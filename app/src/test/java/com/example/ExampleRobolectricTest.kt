package com.example

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DatabaseInitializer
import com.example.data.firebase.FirestoreSyncManager
import com.example.data.repository.AuthRepository
import com.example.viewmodel.AuthViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Jaiti Foundation", appName)
  }

  @Test
  fun `verify all drawables and launcher icons decode properly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    
    // Verify drawable resources decode as valid bitmap without crashing or returning null
    val logoRes = context.resources.openRawResource(R.drawable.jaiti_logo)
    val bitmap = BitmapFactory.decodeStream(logoRes)
    assertNotNull("jaiti_logo should decode to a valid non-null Bitmap", bitmap)
    assertTrue("Bitmap width should be > 0", bitmap.width > 0)
    assertTrue("Bitmap height should be > 0", bitmap.height > 0)

    val launcherRes = context.resources.openRawResource(R.mipmap.ic_launcher)
    val launcherBitmap = BitmapFactory.decodeStream(launcherRes)
    assertNotNull("ic_launcher should decode to a valid non-null Bitmap", launcherBitmap)
  }

  @Test
  fun `launch main activity and verify full lifecycle`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    assertNotNull(activity)
    controller.resume()
    controller.pause()
    controller.stop()
    controller.destroy()
  }

  @Test
  fun `verify application initialization`() {
    val app = ApplicationProvider.getApplicationContext<JaitiApplication>()
    assertNotNull(app)
  }

  @Test
  fun `verify database and auth repository initialization`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = AppDatabase.getInstance(context)
    val syncManager = FirestoreSyncManager(context, database)
    val authRepo = AuthRepository(database.userDao(), database, syncManager)
    val authVm = AuthViewModel(authRepo)

    assertNull(authVm.currentUser.value)
    assertFalse(authVm.isLoading.value)
  }

  @Test
  fun `verify offline login with default admin credentials succeeds`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = AppDatabase.getInstance(context)
    DatabaseInitializer.seedDatabaseIfEmpty(database)

    val authRepo = AuthRepository(database.userDao(), database, null)
    val authVm = AuthViewModel(authRepo)

    val loginResult = authVm.login("jaitifoundation@gmail.com", "Admin@123")
    assertTrue("Default admin login should succeed offline", loginResult)
    assertNotNull("Current user should be non-null after successful login", authVm.currentUser.value)
    assertEquals("jaitifoundation@gmail.com", authVm.currentUser.value?.email)
  }
}


