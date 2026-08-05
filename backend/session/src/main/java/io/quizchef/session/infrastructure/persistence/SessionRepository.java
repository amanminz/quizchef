package io.quizchef.session.infrastructure.persistence;

import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionState;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    /**
     * The one active (non-archived) session for a PIN, if any. A PIN is
     * unique among active sessions and reused only after archival, so this
     * returns at most one row.
     */
    Optional<Session> findBySessionPinValueAndStateNot(String sessionPinValue, SessionState state);

    boolean existsBySessionPinValueAndStateNot(String sessionPinValue, SessionState state);

    /**
     * The same lookup, but taking a write lock on the session row for the
     * rest of the transaction.
     *
     * <p>For joining, and only for joining. A join appends to the session's
     * roster, which makes it a read-modify-write of one shared row: a whole
     * room scanning the QR code at once means dozens of transactions loading
     * the same version and all but one failing the optimistic check. Waiting
     * a few milliseconds for a lock is the right trade there — nobody is
     * refused, and joins simply queue.
     *
     * <p>Every other caller keeps the ordinary optimistic path. Locking is
     * per-operation on purpose: it belongs where contention is expected and
     * the work is short, not spread across the engine.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Session s where s.sessionPin.value = :pin and s.state <> :excluded")
    Optional<Session> findAndLockBySessionPinValueAndStateNot(@Param("pin") String pin,
                                                              @Param("excluded") SessionState excluded);
}
