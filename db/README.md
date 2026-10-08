# Database changes

The baseline schema is the existing `ChessDB` database on the developer's SQL Server; its DDL is
not tracked here. This folder tracks every change made on top of that baseline.

Apply scripts in numeric order to `ChessDB` and to the schema-only test database `ChessDB_Test`.
Each script is idempotent. Example (Windows authentication, SQL Server Express):

```powershell
sqlcmd -S "localhost\SQLEXPRESS" -E -C -b -d ChessDB -i db\migrations\001_invitation_host_color.sql
sqlcmd -S "localhost\SQLEXPRESS" -E -C -b -d ChessDB_Test -i db\migrations\001_invitation_host_color.sql
```

| Script | Feature | Change |
| --- | --- | --- |
| `001_invitation_host_color.sql` | F04 | `Invitation.host_color` (WHITE, BLACK, RANDOM; default RANDOM) |
