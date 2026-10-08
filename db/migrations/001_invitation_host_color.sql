-- F04: the host chooses their color (WHITE, BLACK or RANDOM) when creating an invitation.
-- Colors are fixed on OnlineGame when the invitation is claimed; RANDOM is resolved at claim time.
-- Idempotent: safe to run more than once against ChessDB and ChessDB_Test.
IF COL_LENGTH('dbo.Invitation', 'host_color') IS NULL
BEGIN
    ALTER TABLE dbo.Invitation
        ADD host_color varchar(6) NOT NULL
            CONSTRAINT DF_Invitation_host_color DEFAULT ('RANDOM')
            CONSTRAINT CK_Invitation_host_color CHECK (host_color IN ('WHITE', 'BLACK', 'RANDOM'));
END;
