import { ProgressStepper } from "@/components/common/ProgressStepper";

const STEPS = [
  { key: "account", label: "Account created" },
  { key: "host-access", label: "Host access" },
  { key: "author", label: "Start authoring" }
];

/** Where the caller stands in becoming a host — display only. */
export function OnboardingProgress({ isHost }: { isHost: boolean }) {
  return <ProgressStepper steps={STEPS} currentKey={isHost ? "author" : "host-access"} />;
}
