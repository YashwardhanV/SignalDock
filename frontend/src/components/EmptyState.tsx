import { Inbox } from "lucide-react";

export function EmptyState({ message }: { message: string }) {
  return <div className="empty-state"><Inbox size={22} aria-hidden="true" /><span>{message}</span></div>;
}

