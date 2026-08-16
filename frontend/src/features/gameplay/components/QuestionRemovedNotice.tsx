import { Info } from "lucide-react";

/**
 * What a player sees when the Quiz Master pulls the question they were
 * looking at.
 *
 * Bilingual and unconditional rather than following the device's chosen
 * language. Every other screen renders one language because the player
 * picked it; this one interrupts a game in progress to explain why the
 * question vanished, and a player who misreads *that* has no way to ask.
 *
 * It says nothing about the question itself — not its answer, not who had
 * answered, not what it would have been worth. The room never finished it,
 * so its correct option is a spoiler rather than a reveal, and there is no
 * score change to explain because the question now contributes to nothing.
 */
export function QuestionRemovedNotice() {
  return (
    <div
      role="status"
      className="flex flex-col items-center gap-3 rounded-lg border border-dashed px-6 py-10 text-center"
    >
      <Info aria-hidden className="h-6 w-6 text-muted-foreground" />
      <div className="space-y-1">
        <p className="text-sm font-medium">This question was removed by the Quiz Master.</p>
        <p className="text-sm text-muted-foreground">Get ready for the next question.</p>
      </div>
      <div className="space-y-1" lang="hi">
        <p className="text-sm font-medium">क्विज़ मास्टर ने इस सवाल को हटा दिया है।</p>
        <p className="text-sm text-muted-foreground">अगले सवाल के लिए तैयार हो जाइए।</p>
      </div>
    </div>
  );
}
