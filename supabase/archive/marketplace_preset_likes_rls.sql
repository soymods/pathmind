-- RLS policies for preset_likes.
-- Run this once in the Supabase SQL editor.
--
-- Background: preset_likes has no RLS, so any authenticated user can query
-- another user's liked presets directly via PostgREST.
-- The toggle/delete RPCs use security definer and bypass RLS, so they are unaffected.

alter table public.preset_likes enable row level security;

-- Users can only read their own likes.
drop policy if exists "Users can read their own preset likes" on public.preset_likes;
create policy "Users can read their own preset likes"
on public.preset_likes
for select
to authenticated
using (user_id = auth.uid());

-- Users can only insert their own likes (belt-and-suspenders alongside the RPC).
drop policy if exists "Users can insert their own preset likes" on public.preset_likes;
create policy "Users can insert their own preset likes"
on public.preset_likes
for insert
to authenticated
with check (user_id = auth.uid());

-- Users can only delete their own likes (belt-and-suspenders alongside the RPC).
drop policy if exists "Users can delete their own preset likes" on public.preset_likes;
create policy "Users can delete their own preset likes"
on public.preset_likes
for delete
to authenticated
using (user_id = auth.uid());
