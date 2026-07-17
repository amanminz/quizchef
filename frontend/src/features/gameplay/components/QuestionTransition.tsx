import { useEffect, useState, type ReactNode } from "react";
import { cn } from "@/utils/cn";

/**
 * Fades its children in whenever `transitionKey` changes (a new question
 * id) — a small polish touch, not a state-deciding animation: the content
 * underneath is already the server's current truth the instant this
 * mounts, the fade is purely cosmetic.
 */
export function QuestionTransition({
  transitionKey,
  children
}: {
  transitionKey: string;
  children: ReactNode;
}) {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    setVisible(false);
    const frame = requestAnimationFrame(() => setVisible(true));
    return () => cancelAnimationFrame(frame);
  }, [transitionKey]);

  return (
    <div className={cn("transition-opacity duration-300", visible ? "opacity-100" : "opacity-0")}>
      {children}
    </div>
  );
}
