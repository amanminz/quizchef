import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/common/Card";
import type { Schemas } from "@/types/api";

type SessionSettings = Schemas["SessionSettingsDto"];

function SettingRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 py-1.5 text-sm">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  );
}

/**
 * The session's configuration, read-only by design: `CreateSessionRequest`
 * carries no settings — the server assigns them (RFC-004), and validation
 * is server-authoritative. Before creation this renders the explanation;
 * after creation it renders the server-assigned values from the summary.
 * Host-editable settings arrive when the backend grows a settings surface.
 */
export function ConfigurationSection({ settings }: { settings: SessionSettings | undefined }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Session settings</CardTitle>
        <CardDescription>
          Assigned by the server when the session is created.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {settings ? (
          <dl className="divide-y">
            <SettingRow
              label="Max participants"
              value={settings.maxParticipants != null ? String(settings.maxParticipants) : "Unlimited"}
            />
            <SettingRow label="Late join" value={settings.allowLateJoin ? "Allowed" : "Not allowed"} />
            <SettingRow
              label="Reconnect"
              value={settings.allowReconnect ? "Allowed" : "Not allowed"}
            />
            <SettingRow
              label="Live leaderboard"
              value={settings.showLiveLeaderboard ? "Shown" : "Hidden"}
            />
          </dl>
        ) : (
          <p className="text-sm text-muted-foreground">
            Defaults are applied on creation and shown on the session page.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
