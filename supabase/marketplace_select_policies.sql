alter table public.marketplace_presets enable row level security;

drop policy if exists "Owners can read their marketplace presets" on public.marketplace_presets;

create policy "Owners can read their marketplace presets"
on public.marketplace_presets
for select
to authenticated
using (author_user_id = auth.uid());
