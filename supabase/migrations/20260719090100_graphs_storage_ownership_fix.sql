-- Fix: the public "graphs" bucket's write policies only checked bucket_id,
-- with no ownership scoping at all. Any authenticated user could upload,
-- overwrite, or delete ANY file in the bucket, including other users'
-- published presets. "private_graphs" already scopes correctly via
-- (storage.foldername(name))[1] = auth.uid()::text (the app writes every
-- object as "<userId>/<slug>.json" via MarketplaceService.buildStoragePath,
-- for both buckets) -- this migration brings "graphs" in line with that.

drop policy if exists "authenticated users can upload graph files" on storage.objects;
drop policy if exists "authenticated users can update graph files" on storage.objects;
drop policy if exists "authenticated users can delete graph files" on storage.objects;
-- "graph files are publicly readable" is intentional (published presets are
-- public) and is left as-is.

create policy "Users can upload their own graph files"
on storage.objects
for insert
to authenticated
with check (
    bucket_id = 'graphs'
    and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "Users can update their own graph files"
on storage.objects
for update
to authenticated
using (
    bucket_id = 'graphs'
    and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
    bucket_id = 'graphs'
    and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "Users can delete their own graph files"
on storage.objects
for delete
to authenticated
using (
    bucket_id = 'graphs'
    and (storage.foldername(name))[1] = auth.uid()::text
);
