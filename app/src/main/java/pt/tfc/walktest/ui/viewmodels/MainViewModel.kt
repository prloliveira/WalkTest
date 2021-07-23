package pt.tfc.walktest.ui.viewmodels

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pt.tfc.walktest.db.Session
import pt.tfc.walktest.other.SortType
import pt.tfc.walktest.repositories.MainRepository

class MainViewModel @ViewModelInject constructor(
    private val mainRepository: MainRepository
) : ViewModel() {

    private val sessionsSortedByDate = mainRepository.getAllCapturesSortedByDate()
    private val sessionsSortedByDistance = mainRepository.getAllCapturesSortedByDistance()
    private val sessionsSortedByTimeInMillis = mainRepository.getAllCapturesSortedByTimeInMillis()

    val sessions = MediatorLiveData<List<Session>>()

    var sortType = SortType.DATE

    init {
        sessions.addSource(sessionsSortedByDate) { result ->
            if (sortType == SortType.DATE) {
                result?.let { sessions.value = it }
            }
        }
        sessions.addSource(sessionsSortedByDistance) { result ->
            if (sortType == SortType.DISTANCE) {
                result?.let { sessions.value = it }
            }
        }
        sessions.addSource(sessionsSortedByTimeInMillis) { result ->
            if (sortType == SortType.RUNNING_TIME) {
                result?.let { sessions.value = it }
            }
        }
    }

    fun sortRuns(sortType: SortType) = when (sortType) {
        SortType.DATE -> sessionsSortedByDate.value?.let { sessions.value = it }
        SortType.RUNNING_TIME -> sessionsSortedByTimeInMillis.value?.let { sessions.value = it }
        SortType.DISTANCE -> sessionsSortedByDistance.value?.let { sessions.value = it }
    }.also {
        this.sortType = sortType
    }

    fun insertSession(session: Session) = viewModelScope.launch {
        mainRepository.insertRun(session)
    }

    fun deleteSession(session: Session) = viewModelScope.launch {
        mainRepository.deleteSession(session)
    }

}