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
     * <p>For joining. A join appends to the session's roster, which makes it
     * a read-modify-write of one shared row: a whole room scanning the QR
     * code at once means dozens of transactions loading the same version and
     * all but one failing the optimistic check. Waiting a few milliseconds
     * for a lock is the right trade there — nobody is refused, and joins
     * simply queue.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Session s where s.sessionPin.value = :pin and s.state <> :excluded")
    Optional<Session> findAndLockBySessionPinValueAndStateNot(@Param("pin") String pin,
                                                              @Param("excluded") SessionState excluded);

    /**
     * By id, taking a write lock on the session row for the rest of the
     * transaction.
     *
     * <p>For the three operations that must not interleave with each other:
     * accepting an answer, correcting a question, and removing one. The
     * problem the lock solves is specific — a correction or removal reverses
     * points across every participant, while answer submission only *reads*
     * the session and so bumps no version to lose an optimistic check
     * against. Without serialization, an answer committing alongside a
     * removal survives it: the question is gone from the sequence but its
     * points are still in someone's total. The session row is the natural
     * thing to serialize on, since all three already load it.
     *
     * <p>Every other caller keeps the ordinary optimistic path. Locking is
     * per-operation on purpose: it belongs where two writers genuinely race
     * and the work is short, not spread across the engine.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Session s where s.id = :id")
    Optional<Session> findAndLockById(@Param("id") UUID id);
}
