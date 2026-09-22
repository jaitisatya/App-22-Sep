package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.dao.AttendanceDao
import com.example.data.dao.ClassDao
import com.example.data.dao.StudentDao
import com.example.data.dao.TestExamDao
import com.example.data.dao.UserDao
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.entity.ClassEntity
import com.example.data.entity.ClassTestEntity
import com.example.data.entity.StudentEntity
import com.example.data.entity.StudentTestMarksEntity
import com.example.data.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        ClassEntity::class,
        StudentEntity::class,
        AttendanceRecordEntity::class,
        ClassTestEntity::class,
        StudentTestMarksEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun classDao(): ClassDao
    abstract fun studentDao(): StudentDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun testExamDao(): TestExamDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jaiti_attendance_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
