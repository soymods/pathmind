alter table public.marketplace_presets enable row level security;

drop policy if exists "Public can read published marketplace presets" on public.marketplace_presets;
create policy "Public can read published marketplace presets"
on public.marketplace_presets
for select
to anon, authenticated
using (published = true);

drop policy if exists "Owners can read their marketplace presets" on public.marketplace_presets;
create policy "Owners can read their marketplace presets"
on public.marketplace_presets
for select
to authenticated
using (author_user_id = auth.uid());

drop policy if exists "Marketplace moderators can read all presets" on public.marketplace_presets;
create policy "Marketplace moderators can read all presets"
on public.marketplace_presets
for select
to authenticated
using (public.is_marketplace_moderator(auth.uid()));
