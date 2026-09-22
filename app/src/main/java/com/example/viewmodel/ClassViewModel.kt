package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.entity.ClassEntity
import com.example.data.entity.UserEntity
import com.example.data.repository.ClassRepository
import com.example.data.repository.StudentRepository
import com.example.data.repository.AuthRepository
import com.example.data.dao.UserDao
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ClassViewModel(
    private val classRepository: ClassRepository,
    private val studentRepository: StudentRepository,
    private val userDao: UserDao
) : ViewModel() {

    val allClasses: StateFlow<List<ClassEntity>> = classRepository.allClasses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeClasses: StateFlow<List<ClassEntity>> = classRepository.activeClasses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTeachers: StateFlow<List<UserEntity>> = userDao.getAllTeachers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getAssignedClasses(classIds: List<String>): Flow<List<ClassEntity>> {
        if (classIds.isEmpty()) return flowOf(emptyList())
        return classRepository.getClassesByIds(classIds)
    }

    fun addClass(className: String, roomOrLocation: String, teacherId: String, teacherName: String) {
        if (className.isBlank()) return
        viewModelScope.launch {
            val classId = "CLASS_${System.currentTimeMillis()}"
            val newClass = ClassEntity(
                classId = classId,
                className = className.trim(),
                roomOrLocation = roomOrLocation.trim(),
                primaryTeacherId = teacherId,
                primaryTeacherName = teacherName,
                active = true
            )
            classRepository.addClass(newClass)

            // Update assigned classes for assigned teacher if selected
            if (teacherId.isNotBlank()) {
                val teacher = userDao.getUserById(teacherId)
                if (teacher != null) {
                    val assignedList = teacher.getAssignedClassIds().toMutableList()
                    if (!assignedList.contains(classId)) {
                        assignedList.add(classId)
                        val updatedTeacher = teacher.copy(assignedClassIdsCsv = assignedList.joinToString(","))
                        userDao.updateUser(updatedTeacher)
                    }
                }
            }
        }
    }

    fun updateClass(classEntity: ClassEntity, newTeacherId: String, newTeacherName: String) {
        viewModelScope.launch {
            val updated = classEntity.copy(
                primaryTeacherId = newTeacherId,
                primaryTeacherName = newTeacherName
            )
            classRepository.updateClass(updated)

            // Update teacher assigned classes
            if (newTeacherId.isNotBlank()) {
                val teacher = userDao.getUserById(newTeacherId)
                if (teacher != null) {
                    val assignedList = teacher.getAssignedClassIds().toMutableList()
                    if (!assignedList.contains(classEntity.classId)) {
                        assignedList.add(classEntity.classId)
                        userDao.updateUser(teacher.copy(assignedClassIdsCsv = assignedList.joinToString(",")))
                    }
                }
            }
        }
    }

    fun updateClassDetails(
        classEntity: ClassEntity,
        newName: String,
        newTeacherName: String,
        newLocation: String = "",
        newTeacherId: String = ""
    ) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val updated = classEntity.copy(
                className = newName.trim(),
                roomOrLocation = newLocation.trim(),
                primaryTeacherId = if (newTeacherId.isNotBlank()) newTeacherId else classEntity.primaryTeacherId,
                primaryTeacherName = newTeacherName.trim()
            )
            classRepository.updateClass(updated)
        }
    }

    fun deleteClass(classEntity: ClassEntity) {
        viewModelScope.launch {
            classRepository.deleteClass(classEntity)
        }
    }

    fun clearAllClasses() {
        viewModelScope.launch {
            classRepository.clearAllClasses()
        }
    }

    fun toggleClassActive(classEntity: ClassEntity) {
        viewModelScope.launch {
            classRepository.updateClass(classEntity.copy(active = !classEntity.active))
        }
    }

    fun addTeacher(username: String, password: String, fullName: String, phone: String, assignedClassIds: List<String>) {
        viewModelScope.launch {
            val newTeacher = UserEntity(
                userId = "USER_${System.currentTimeMillis()}",
                username = username.trim(),
                passwordHash = password.trim(),
                fullName = fullName.trim(),
                role = com.example.data.model.Role.TEACHER,
                email = "${username.trim()}@jaiti.in",
                phone = phone.trim(),
                active = true,
                assignedClassIdsCsv = assignedClassIds.joinToString(",")
            )
            userDao.insertUser(newTeacher)
        }
    }

    fun updateTeacher(teacher: UserEntity) {
        viewModelScope.launch {
            userDao.updateUser(teacher)
        }
    }
}
