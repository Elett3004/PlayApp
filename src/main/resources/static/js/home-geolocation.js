(() => {
    'use strict';

    const button = document.getElementById('btnUbicacionInicio');
    if (!button) return;

    const status = document.getElementById('geoInicioStatus');
    const spinner = document.getElementById('geoInicioSpinner');
    const mapsLink = document.getElementById('geoInicioMapa');
    const mapElement = document.getElementById('homeLocationMap');
    const defaultPoint = [10.399667, -75.555001];
    let map;
    let deliveryMarker;
    let currentLocationMarker;

    if (mapElement && window.L) {
        map = L.map(mapElement).setView(defaultPoint, 15);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        }).addTo(map);

        deliveryMarker = L.marker(defaultPoint)
            .addTo(map)
            .bindPopup('<b>Bocagrande</b><br>Punto base de despacho');
    }

    function showStatus(message, type) {
        status.classList.remove('d-none', 'alert-info', 'alert-warning', 'alert-danger');
        status.classList.add(type);
        status.textContent = message;
    }

    function setLoading(isLoading) {
        button.disabled = isLoading;
        button.setAttribute('aria-busy', String(isLoading));
        spinner.classList.toggle('d-none', !isLoading);
    }

    function getErrorMessage(error) {
        switch (error.code) {
            case 1:
                return 'Permiso de ubicación denegado. Habilítalo en los permisos de este sitio y vuelve a intentarlo.';
            case 2:
                return 'No se pudo determinar tu ubicación. Comprueba que el GPS o la ubicación del dispositivo esté activado.';
            case 3:
                return 'La ubicación tardó demasiado en responder. Inténtalo nuevamente.';
            default:
                return 'No fue posible obtener tu ubicación. Inténtalo nuevamente.';
        }
    }

    function locateUser() {
        if (!navigator.geolocation) {
            showStatus('Este navegador no admite geolocalización.', 'alert-warning');
            return;
        }

        const isLocalhost = ['localhost', '127.0.0.1', '::1', '[::1]'].includes(window.location.hostname);
        if (window.isSecureContext === false && !isLocalhost) {
            showStatus('La ubicación requiere HTTPS o localhost.', 'alert-warning');
            return;
        }

        status.classList.add('d-none');
        mapsLink.classList.add('d-none');
        setLoading(true);

        navigator.geolocation.getCurrentPosition((position) => {
            const latitude = position.coords.latitude;
            const longitude = position.coords.longitude;
            const point = [latitude, longitude];

            if (map) {
                if (!currentLocationMarker) {
                    const icon = L.divIcon({
                        className: 'playapp-current-location-icon',
                        html: '<span></span>',
                        iconSize: [26, 26],
                        iconAnchor: [13, 13]
                    });
                    currentLocationMarker = L.marker(point, {
                        icon,
                        title: 'Tu ubicación actual',
                        alt: 'Tu ubicación actual',
                        zIndexOffset: 1000
                    }).addTo(map).bindPopup('<b>Tu ubicación actual</b>');
                } else {
                    currentLocationMarker.setLatLng(point);
                }
                map.flyTo(point, Math.max(map.getZoom(), 16), { duration: 1 });
            }

            showStatus(`Ubicación obtenida: ${latitude.toFixed(6)}, ${longitude.toFixed(6)}.`, 'alert-info');
            mapsLink.href = `https://www.google.com/maps?q=${encodeURIComponent(`${latitude},${longitude}`)}&z=16`;
            mapsLink.classList.remove('d-none');
            setLoading(false);
        }, (error) => {
            showStatus(getErrorMessage(error), 'alert-warning');
            setLoading(false);
        }, {
            enableHighAccuracy: true,
            maximumAge: 0,
            timeout: 15000
        });
    }

    button.addEventListener('click', locateUser);
})();
