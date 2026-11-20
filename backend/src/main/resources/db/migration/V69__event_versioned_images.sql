-- 이벤트 배너·썸네일을 내용 해시를 넣은 이름으로 바꾼다(S15P31A207-329). nginx가 해시 이름 그림만 하루 캐시한다.
-- 운영자가 이미 다른 주소로 바꾼 행은 건드리지 않는다(처음 넣은 주소와 같을 때만 바꾼다). 옛 이름 파일은 옛 앱을 위해 남긴다.
UPDATE event SET banner_image_url = '/events/coach-challenge/banner.57f8f71a.png'
 WHERE slug = 'coach-challenge' AND banner_image_url = '/events/coach-challenge/banner.png';
UPDATE event SET thumbnail_image_url = '/events/coach-challenge/thumb.daab8bb1.png'
 WHERE slug = 'coach-challenge' AND thumbnail_image_url = '/events/coach-challenge/thumb.png';
UPDATE event SET banner_image_url = '/events/beta-1km/banner.59e500fa.png'
 WHERE slug = 'beta-1km' AND banner_image_url = '/events/beta-1km/banner.png';
UPDATE event SET thumbnail_image_url = '/events/beta-1km/thumb.ecb76844.png'
 WHERE slug = 'beta-1km' AND thumbnail_image_url = '/events/beta-1km/thumb.png';
