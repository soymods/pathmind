-- Enforce a server-side size cap on the marketplace preset graph buckets.
-- Run this once in the Supabase SQL editor.
--
-- The client already rejects preset files over 5 MB before uploading (see
-- MarketplaceService.requireWithinSizeLimit), but that check is advisory only:
-- any client talking directly to the Storage API could skip it. The bucket-level
-- file_size_limit below is the actual enforcement boundary.

update storage.buckets
set file_size_limit = 5242880, -- 5 MB
    allowed_mime_types = array['application/json', 'text/plain']
where id in ('graphs', 'private_graphs');

-- If the buckets don't exist yet in this project, create them with the same cap.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
    ('graphs', 'graphs', true, 5242880, array['application/json', 'text/plain']),
    ('private_graphs', 'private_graphs', false, 5242880, array['application/json', 'text/plain'])
on conflict (id) do nothing;
