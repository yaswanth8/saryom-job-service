-- Pin the search_path of the three identity helpers.
--
-- A function with no search_path of its own resolves unqualified names using
-- whatever path its caller has set. These three only call built-ins, which
-- Postgres finds in pg_catalog before anything on the path, so nothing could
-- actually be substituted. But the row level security policies stand on these
-- functions, and "nothing could" is a weaker footing than "nothing can": an
-- empty path leaves pg_catalog as the only place a name in them resolves.
-- (Supabase's security advisor flags the unpinned form, lint 0011.)

ALTER FUNCTION request_uid() SET search_path = '';
ALTER FUNCTION request_is_admin() SET search_path = '';
ALTER FUNCTION request_token_hash() SET search_path = '';
