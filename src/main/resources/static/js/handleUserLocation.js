(() => {
    'use strict';

    const apiKey = 'pk.a921cf52c140515c913de88dd831f598';
    const defaultPoint = [10.394598592365298, -75.5571199005398];
    let map;
    let deliveryMarker;
    let currentLocationMarker;

    function initializeMap() {
        const mapElement = document.getElementById('map');
        if (!mapElement || !window.L) return;

        map = L.map(mapElement).setView(defaultPoint, 16);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        }).addTo(map);

        deliveryMarker = L.marker(defaultPoint).addTo(map)
            .bindPopup('<b>Punto de entrega</b>')
            .openPopup();

        map.on('click', async (event) => {
            const { lat, lng } = event.latlng;
            updateDeliveryPoint(lat, lng);
            document.getElementById('address').value = await getAddress(lat, lng);
        });

        const locationButton = document.getElementById('btnGeolocalizacion');
        if (locationButton) {
            locationButton.addEventListener('click', locateUser);
        }
    }

    function updateDeliveryPoint(latitude, longitude) {
        deliveryMarker.setLatLng([latitude, longitude]);
        document.getElementById('latitud').value = latitude;
        document.getElementById('longitud').value = longitude;
    }

    function showLocationMessage(message, type) {
        const alert = document.getElementById('geoErrorAlert');
        if (!alert) return;

        alert.classList.remove('d-none', 'alert-warning', 'alert-danger', 'alert-info');
        alert.classList.add(type);
        alert.textContent = message;
    }

    function hideLocationMessage() {
        const alert = document.getElementById('geoErrorAlert');
        if (alert) alert.classList.add('d-none');
    }

    function getLocationErrorMessage(error) {
        switch (error.code) {
            case 1:
                return 'No se concedió el permiso de ubicación. Habilítalo en los permisos de este sitio desde tu navegador y vuelve a intentarlo.';
            case 2:
                return 'No se pudo determinar tu ubicación. Comprueba que la ubicación/GPS del dispositivo esté activada e inténtalo de nuevo.';
            case 3:
                return 'La ubicación tardó demasiado en responder. Inténtalo nuevamente.';
            default:
                return 'No fue posible obtener tu ubicación. Inténtalo nuevamente.';
        }
    }

    function setLocationLoading(isLoading) {
        const button = document.getElementById('btnGeolocalizacion');
        const spinner = document.getElementById('geoLocationSpinner');
        if (button) {
            button.disabled = isLoading;
            button.setAttribute('aria-busy', String(isLoading));
        }
        if (spinner) spinner.classList.toggle('d-none', !isLoading);
    }

    function locateUser() {
        if (!navigator.geolocation) {
            showLocationMessage('Este navegador no permite solicitar la ubicación del dispositivo.', 'alert-warning');
            return;
        }

        const isLocalhost = ['localhost', '127.0.0.1', '::1', '[::1]'].includes(window.location.hostname);
        if (window.isSecureContext === false && !isLocalhost) {
            showLocationMessage('El navegador solo permite compartir la ubicación en una conexión segura (HTTPS) o en localhost.', 'alert-warning');
            return;
        }

        hideLocationMessage();
        setLocationLoading(true);
        navigator.geolocation.getCurrentPosition(handleLocationSuccess, (error) => {
            showLocationMessage(getLocationErrorMessage(error), 'alert-warning');
            setLocationLoading(false);
        }, {
            enableHighAccuracy: true,
            maximumAge: 0,
            timeout: 15000
        });
    }

    async function handleLocationSuccess(position) {
        const latitude = position.coords.latitude;
        const longitude = position.coords.longitude;
        const point = [latitude, longitude];

        updateDeliveryPoint(latitude, longitude);
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
        document.getElementById('address').value = await getAddress(latitude, longitude);
        showLocationMessage('Ubicación obtenida. El punto de entrega se actualizó en el mapa.', 'alert-info');
        setLocationLoading(false);
    }

    async function getAddress(latitude, longitude) {
        const url = `https://us1.locationiq.com/v1/reverse?key=${apiKey}&lat=${latitude}&lon=${longitude}&format=json`;
        try {
            const response = await fetch(url);
            if (!response.ok) return 'No se encontró dirección';
            const data = await response.json();
            return data.display_name || 'No se encontró dirección';
        } catch (error) {
            console.error('Error fetching address:', error);
            return 'No se pudo recuperar la dirección';
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializeMap, { once: true });
    } else {
        initializeMap();
    }
})();
