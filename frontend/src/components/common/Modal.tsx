import { useEffect, useRef, type ReactNode } from "react";
import { X } from "lucide-react";
import { cn } from "@/utils/cn";

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  className?: string;
}

/**
 * A controlled modal on the native <dialog> element — focus trapping, ESC
 * handling, and top-layer stacking come from the platform.
 */
export function Modal({ open, onClose, title, children, className }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      onClick={(event) => {
        // A click on the backdrop (the dialog element itself) closes.
        if (event.target === dialogRef.current) {
          onClose();
        }
      }}
      className={cn(
        "w-full max-w-md rounded-lg border bg-card p-0 text-card-foreground shadow-elevation-3",
        "backdrop:bg-black/50",
        className
      )}
    >
      <div className="flex items-center justify-between border-b p-4">
        <h2 className="text-lg font-semibold">{title}</h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          className="rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
      <div className="p-4">{children}</div>
    </dialog>
  );
}
