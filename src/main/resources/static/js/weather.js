(() => {
    'use strict';
    const element = document.getElementById('weather-map');
    if (!element) return;
    const fallback = document.getElementById('weather-map-fallback');
    const button = document.getElementById('recenter-weather-map');
    const latitude = Number(element.dataset.latitude);
    const longitude = Number(element.dataset.longitude);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        fallback.hidden = false;
        return;
    }
    const link = document.getElementById('weather-map-link');
    link.href = `https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=15/${latitude}/${longitude}`;
    if (!window.L) {
        fallback.hidden = false;
        return;
    }
    const point = [latitude, longitude];
    const map = L.map(element, { scrollWheelZoom: false }).setView(point, 15);
    const tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    }).addTo(map);
    const icon = L.divIcon({ className: 'weather-pin', html: '<span></span>', iconSize: [40, 40], iconAnchor: [20, 20] });
    L.marker(point, { icon, title: 'Punto de consulta: Playa de Bocagrande', alt: 'Playa de Bocagrande' })
        .addTo(map).bindTooltip('Bocagrande · Punto de consulta', { permanent: true, direction: 'top', offset: [0, -18], className: 'weather-map-label' });
    let tilesLoaded = false;
    tiles.on('tileload', () => { tilesLoaded = true; fallback.hidden = true; });
    tiles.on('tileerror', () => { if (!tilesLoaded) fallback.hidden = false; });
    button.hidden = false;
    button.addEventListener('click', () => map.setView(point, 15));
    if (window.ResizeObserver) new ResizeObserver(() => map.invalidateSize()).observe(element);
})();
