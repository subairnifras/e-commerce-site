const api = '/api';

let productCache = [];
let currentCart = null;

/*
 * The browser stores only the temporary login session.
 * Passwords and API keys are never stored in JavaScript.
 */
function session() {
    try {
        return JSON.parse(
            sessionStorage.getItem('socSession')
        );
    } catch {
        return null;
    }
}

function customer() {
    try {
        return JSON.parse(
            sessionStorage.getItem('socCustomer')
        );
    } catch {
        return null;
    }
}

function requestHeaders() {
    const currentSession = session();

    const result = {
        'Content-Type': 'application/json'
    };

    if (currentSession?.token) {
        result.Authorization =
            `Bearer ${currentSession.token}`;
    }

    return result;
}

async function call(path, options = {}) {
    const response = await fetch(api + path, {
        ...options,
        headers: {
            ...requestHeaders(),
            ...(options.headers || {})
        }
    });

    const responseText = await response.text();

    if (!response.ok) {
        let message = responseText;

        try {
            const errorBody = JSON.parse(responseText);

            message =
                errorBody.error ||
                errorBody.message ||
                responseText;
        } catch {
            // Response was not JSON.
        }

        if (response.status === 401 && session()) {
            clearSession();
            applySession();
        }

        throw new Error(
            message ||
            `Request failed (${response.status})`
        );
    }

    if (!responseText) {
        return null;
    }

    const contentType =
        response.headers.get('content-type') || '';

    return contentType.includes('json')
        ? JSON.parse(responseText)
        : responseText;
}

function hideAllPortals() {
    document.querySelector('#auth')
        .classList.add('hidden');

    document.querySelector('#shop')
        .classList.add('hidden');

    document.querySelector('#admin')
        .classList.add('hidden');
}

function show(id) {
    const currentSession = session();

    if (!currentSession) {
        hideAllPortals();

        document.querySelector('#auth')
            .classList.remove('hidden');

        return;
    }

    if (
        id === 'admin' &&
        currentSession.role !== 'ADMIN'
    ) {
        alert('Administrator access is required');
        return;
    }

    if (
        id === 'shop' &&
        currentSession.role !== 'CUSTOMER'
    ) {
        alert('Customer access is required');
        return;
    }

    hideAllPortals();

    document.querySelector(`#${id}`)
        .classList.remove('hidden');
}

function applySession() {
    const currentSession = session();

    const customerButton =
        document.querySelector('#customerPortalButton');

    const cartButton =
        document.querySelector('#cartButton');

    const adminButton =
        document.querySelector('#adminPortalButton');

    const logoutButton =
        document.querySelector('#logoutButton');

    customerButton.classList.add('hidden');
    cartButton.classList.add('hidden');
    adminButton.classList.add('hidden');
    logoutButton.classList.add('hidden');

    hideAllPortals();

    if (!currentSession) {
        document.querySelector('#auth')
            .classList.remove('hidden');

        return;
    }

    logoutButton.classList.remove('hidden');

    if (currentSession.role === 'ADMIN') {
        adminButton.classList.remove('hidden');

        document.querySelector('#activeAdmin')
            .textContent =
            `Logged in as ${currentSession.username}`;

        show('admin');
        loadOrders();
        return;
    }

    customerButton.classList.remove('hidden');
    cartButton.classList.remove('hidden');

    show('shop');
    renderCustomer();
    loadProducts();
    loadCart();
}

async function login() {
    const result =
        document.querySelector('#loginResult');

    const username =
        document.querySelector('#loginUsername')
            .value
            .trim();

    const password =
        document.querySelector('#loginPassword')
            .value;

    if (!username || !password) {
        result.textContent =
            'Enter your email and password.';
        return;
    }

    result.textContent = 'Logging in...';

    try {
        const loginSession = await call(
            '/auth/login',
            {
                method: 'POST',
                body: JSON.stringify({
                    username,
                    password
                })
            }
        );

        sessionStorage.setItem(
            'socSession',
            JSON.stringify(loginSession)
        );

        if (
            loginSession.role === 'CUSTOMER' &&
            loginSession.customerId
        ) {
            const customerDetails = await call(
                `/customers/${loginSession.customerId}`
            );

            sessionStorage.setItem(
                'socCustomer',
                JSON.stringify(customerDetails)
            );
        } else {
            sessionStorage.removeItem('socCustomer');
        }

        document.querySelector('#loginPassword')
            .value = '';

        result.textContent = '';

        applySession();

    } catch (error) {
        result.textContent = error.message;
    }
}

async function registerCustomer() {
    const result =
        document.querySelector('#registerResult');

    const name =
        document.querySelector('#registerName')
            .value
            .trim();

    const email =
        document.querySelector('#registerEmail')
            .value
            .trim()
            .toLowerCase();

    const password =
        document.querySelector('#registerPassword')
            .value;

    const phone =
        document.querySelector('#registerPhone')
            .value
            .trim();

    const address =
        document.querySelector('#registerAddress')
            .value
            .trim();

    if (
        !name ||
        !email ||
        !password ||
        !phone ||
        !address
    ) {
        result.textContent =
            'Please complete every field.';
        return;
    }

    if (password.length < 8) {
        result.textContent =
            'Password must contain at least 8 characters.';
        return;
    }

    result.textContent =
        'Creating your customer account...';

    try {
        /*
         * First create the customer profile.
         */
        const createdCustomer = await call(
            '/customers',
            {
                method: 'POST',
                body: JSON.stringify({
                    name,
                    email,
                    phone,
                    address
                })
            }
        );

        /*
         * Then create the secure login account linked
         * to the customer profile.
         */
        await call('/auth/register', {
            method: 'POST',
            body: JSON.stringify({
                username: email,
                password,
                customerId: createdCustomer.id
            })
        });

        document.querySelector('#loginUsername')
            .value = email;

        document.querySelector('#loginPassword')
            .value = password;

        result.textContent =
            'Registration successful. Logging in...';

        await login();

    } catch (error) {
        result.textContent = error.message;
    }
}

async function logout() {
    try {
        if (session()) {
            await call('/auth/logout', {
                method: 'POST'
            });
        }
    } catch {
        // Clear the local session even if the service is unavailable.
    }

    clearSession();
    applySession();
}

function clearSession() {
    sessionStorage.removeItem('socSession');
    sessionStorage.removeItem('socCustomer');

    productCache = [];
    currentCart = null;

    document.querySelector('#cartCount')
        .textContent = '0';

    document.querySelector('#cartTotal')
        .textContent = 'Total: LKR 0';
}

function renderCustomer() {
    const selectedCustomer = customer();

    const customerDisplay =
        document.querySelector('#activeCustomer');

    if (!selectedCustomer) {
        customerDisplay.textContent =
            'Customer information unavailable.';
        return;
    }

    customerDisplay.textContent =
        `Buying as ${selectedCustomer.name} · ` +
        `Delivery to ${selectedCustomer.address}`;
}

function showCart() {
    show('shop');

    document.querySelector('#cartPanel')
        .scrollIntoView({
            behavior: 'smooth'
        });

    loadCart();
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}

async function loadProducts() {
    const productContainer =
        document.querySelector('#products');

    try {
        productCache = await call('/products');

        productContainer.innerHTML =
            productCache.map(product => `
                <article class="product">
                    <img
                        src="${
                            escapeHtml(
                                product.imageUrl ||
                                'https://placehold.co/600x400?text=Product'
                            )
                        }"
                        alt="${escapeHtml(product.name)}"
                    >

                    <div>
                        <h3>
                            ${escapeHtml(product.name)}
                        </h3>

                        <p>
                            ${escapeHtml(product.description)}
                        </p>

                        <p class="price">
                            LKR ${Number(product.price).toFixed(2)}
                        </p>

                        <small>
                            ${Number(product.stock)} in stock
                        </small>

                        <div
                            class="product-actions"
                            style="
                                display:grid;
                                grid-template-columns:1fr 1fr;
                                gap:8px;
                                padding:0;
                            "
                        >
                            <button
                                ${product.stock < 1
                                    ? 'disabled'
                                    : ''}
                                onclick="addToCart('${product.id}')"
                            >
                                ${product.stock < 1
                                    ? 'Out of stock'
                                    : 'Add to cart'}
                            </button>

                            <button
                                style="background:#f97316"
                                ${product.stock < 1
                                    ? 'disabled'
                                    : ''}
                                onclick="buyNow('${product.id}')"
                            >
                                Buy now
                            </button>
                        </div>
                    </div>
                </article>
            `).join('') ||
            '<p>No products available.</p>';

    } catch (error) {
        productContainer.innerHTML =
            `<pre>${escapeHtml(error.message)}</pre>`;
    }
}

async function addToCart(productId) {
    const selectedCustomer = customer();

    if (!selectedCustomer) {
        alert('Please log in as a customer');
        return;
    }

    const product = productCache.find(
        item => item.id === productId
    );

    if (!product || product.stock < 1) {
        alert('Product is unavailable');
        return;
    }

    try {
        await call(
            `/carts/${selectedCustomer.id}/items`,
            {
                method: 'POST',
                body: JSON.stringify({
                    productId: product.id,
                    productName: product.name,
                    quantity: 1,
                    unitPrice: product.price
                })
            }
        );

        await loadCart();

        alert(`${product.name} added to cart`);

    } catch (error) {
        alert(error.message);
    }
}

async function buyNow(productId) {
    const selectedCustomer = customer();

    if (!selectedCustomer) {
        alert('Please log in as a customer');
        return;
    }

    const product = productCache.find(
        item => item.id === productId
    );

    if (!product || product.stock < 1) {
        alert('Product is unavailable');
        return;
    }

    if (!confirm(
        `Buy ${product.name} for LKR ${product.price}?`
    )) {
        return;
    }

    try {
        const order = await call('/orders', {
            method: 'POST',
            body: JSON.stringify({
                customerId: selectedCustomer.id,
                customerName: selectedCustomer.name,
                address: selectedCustomer.address,
                items: [{
                    productId: product.id,
                    productName: product.name,
                    quantity: 1,
                    unitPrice: product.price
                }]
            })
        });

        await loadProducts();

        if (confirm(
            `Purchase successful.\n` +
            `Total: LKR ${order.total}\n\n` +
            `Open printable bill?`
        )) {
            openBill(order.id);
        }

    } catch (error) {
        alert(error.message);
    }
}

async function loadCart() {
    const selectedCustomer = customer();

    const cartContainer =
        document.querySelector('#cartItems');

    if (!selectedCustomer) {
        currentCart = null;
        cartContainer.innerHTML =
            '<p>Please log in as a customer.</p>';

        updateCartSummary();
        return;
    }

    try {
        currentCart = await call(
            `/carts/${selectedCustomer.id}`
        );

        const items = currentCart?.items || [];

        if (!items.length) {
            cartContainer.innerHTML =
                '<p>Your cart is empty.</p>';
        } else {
            cartContainer.innerHTML =
                items.map(item => `
                    <div class="cart-row">
                        <strong>
                            ${escapeHtml(item.productName)}
                        </strong>

                        <span>
                            ${item.quantity} ×
                            LKR ${item.unitPrice}
                        </span>

                        <span>
                            LKR ${
                                (
                                    item.quantity *
                                    item.unitPrice
                                ).toFixed(2)
                            }
                        </span>

                        <button
                            onclick="removeFromCart(
                                '${item.productId}'
                            )"
                        >
                            Remove
                        </button>
                    </div>
                `).join('');
        }

        updateCartSummary();

    } catch (error) {
        cartContainer.innerHTML =
            `<pre>${escapeHtml(error.message)}</pre>`;
    }
}

function updateCartSummary() {
    const items = currentCart?.items || [];

    const totalQuantity = items.reduce(
        (total, item) => total + item.quantity,
        0
    );

    document.querySelector('#cartCount')
        .textContent = totalQuantity;

    document.querySelector('#cartTotal')
        .textContent =
        `Total: LKR ${currentCart?.total || 0}`;
}

async function removeFromCart(productId) {
    const selectedCustomer = customer();

    if (!selectedCustomer) {
        return;
    }

    try {
        await call(
            `/carts/${selectedCustomer.id}/items/${productId}`,
            {
                method: 'DELETE'
            }
        );

        await loadCart();

    } catch (error) {
        alert(error.message);
    }
}

async function checkout() {
    const selectedCustomer = customer();

    const result =
        document.querySelector('#checkoutResult');

    if (!selectedCustomer) {
        alert('Please log in as a customer');
        return;
    }

    if (!currentCart?.items?.length) {
        alert('Your cart is empty');
        return;
    }

    try {
        const order = await call('/orders', {
            method: 'POST',
            body: JSON.stringify({
                customerId: selectedCustomer.id,
                customerName: selectedCustomer.name,
                address: selectedCustomer.address,
                items: currentCart.items
            })
        });

        await call(`/carts/${selectedCustomer.id}`, {
            method: 'DELETE'
        });

        result.textContent =
            `Order placed successfully\n` +
            `Order: ${order.id}\n` +
            `Total: LKR ${order.total}`;

        await loadCart();
        await loadProducts();

        if (confirm(
            'Order placed successfully. Open the bill?'
        )) {
            openBill(order.id);
        }

    } catch (error) {
        result.textContent = error.message;
    }
}

async function addProduct() {
    if (session()?.role !== 'ADMIN') {
        alert('Administrator access is required');
        return;
    }

    const name =
        document.querySelector('#pname')
            .value
            .trim();

    const description =
        document.querySelector('#pdescription')
            .value
            .trim();

    const price =
        Number(document.querySelector('#pprice').value);

    const stock =
        Number(document.querySelector('#pstock').value);

    const imageUrl =
        document.querySelector('#pimage')
            .value
            .trim();

    if (
        !name ||
        !description ||
        price <= 0 ||
        stock < 0 ||
        !Number.isInteger(stock)
    ) {
        alert('Please enter valid product information');
        return;
    }

    try {
        await call('/products', {
            method: 'POST',
            body: JSON.stringify({
                name,
                description,
                price,
                stock,
                imageUrl
            })
        });

        alert('Product created successfully');

        document.querySelector('#pname').value = '';
        document.querySelector('#pdescription').value = '';
        document.querySelector('#pprice').value = '';
        document.querySelector('#pstock').value = '';
        document.querySelector('#pimage').value = '';

    } catch (error) {
        alert(error.message);
    }
}

async function loadOrders() {
    const orderContainer =
        document.querySelector('#orders');

    if (session()?.role !== 'ADMIN') {
        orderContainer.innerHTML =
            '<p>Administrator access is required.</p>';
        return;
    }

    try {
        const orderList = await call('/orders');

        orderContainer.innerHTML =
            orderList.map(order => `
                <p>
                    <b>${escapeHtml(order.id)}</b>
                    — ${escapeHtml(order.customerName)}
                    — LKR ${Number(order.total).toFixed(2)}
                    — ${escapeHtml(order.status)}

                    <button
                        onclick="openBill('${order.id}')"
                    >
                        Open bill
                    </button>
                </p>
            `).join('') ||
            '<p>No orders available.</p>';

    } catch (error) {
        orderContainer.innerHTML =
            `<pre>${escapeHtml(error.message)}</pre>`;
    }
}

async function openBill(orderId) {
    try {
        const billHtml = await call(
            `/orders/${orderId}/bill`
        );

        const billWindow = window.open();

        if (!billWindow) {
            alert('Please allow pop-ups to open the bill');
            return;
        }

        billWindow.document.write(billHtml);
        billWindow.document.close();

    } catch (error) {
        alert(error.message);
    }
}

/* Start application using the saved browser session. */
applySession();