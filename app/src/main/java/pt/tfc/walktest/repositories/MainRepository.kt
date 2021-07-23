package pt.tfc.walktest.repositories

import pt.tfc.walktest.db.Session
import pt.tfc.walktest.db.SessionDAO
import javax.inject.Inject

class MainRepository @Inject constructor(
    val sessionDAO: SessionDAO
) {
    suspend fun insertRun(session: Session) = sessionDAO.insertSession(session)

    suspend fun deleteSession(session: Session) = sessionDAO.deleteSession(session)

    fun getAllCapturesSortedByDate() = sessionDAO.getAllSessionsSortedByDate()

    fun getAllCapturesSortedByDistance() = sessionDAO.getAllSessionsSortedByDistance()

    fun getAllCapturesSortedByTimeInMillis() = sessionDAO.getAllSessionsSortedByTimeInMillis()
}