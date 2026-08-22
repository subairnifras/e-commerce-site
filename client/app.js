const api = '/api';

let productCache = [];
let currentCart = null;

// API key is securely added by the Nginx container.
// The browser does not receive or store the API key.
const headers = () => ({
    'Content-Type': 'application/json'
});

const customer = () => {
    try {
        return JSON.parse(
            localStorage.getItem('socCustomer')
        );
    } catch {
        return null;
    }
};

function show(id) {
    document
        .querySelector('#shop')
        .classList.toggle('hidden', id !== 'shop');

    document
        .querySelector('#admin')
        .classList.toggle('hidden', id !== 'admin');
}

function showCart() {
    show('shop');

    document.querySelector('#cartPanel').scrollIntoView({
        behavior: 'smooth'
    });

    loadCart();
}

async function call(path, options = {}) {
    const response = await fetch(api + path, {
        ...options,
        headers: {
            ...headers(),
            ...(options.headers || {})
        }
    });

    const text = await response.text();

    if (!response.ok) {
        throw new Error(
            text || `Request failed (${response.status})`
        );
    }

    const contentType =
        response.headers.get('content-type') || '';

    return contentType.includes('json')
        ? JSON.parse(text)
        : text;
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
                            product.imageUrl ||
                            'https://placehold.co/600x400?text=Product'
                        }"
                        alt="${product.name}"
                    >

                    <div>
                        <h3>${product.name}</h3>

                        <p>${product.description}</p>

                        <p class="price">
                            LKR ${product.price}
                        </p>

                        <small>
                            ${product.stock} in stock
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
                                class="buy-now"
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
            '<p>No products yet.</p>';

    } catch (error) {
        productContainer.innerHTML = `
            <pre>${error.message}</pre>
        `;
    }
}

async function createCustomer() {
    const result =
        document.querySelector('#customerResult');

    const name =
        document.querySelector('#cname').value.trim();

    const email =
        document.querySelector('#cemail').value.trim();

    const phone =
        document.querySelector('#cphone').value.trim();

    const address =
        document.querySelector('#caddress').value.trim();

    if (!name || !email || !phone || !address) {
        result.textContent =
            'Please complete all customer fields.';
        return;
    }

    try {
        const createdCustomer =
            await call('/customers', {
                method: 'POST',
                body: JSON.stringify({
                    name,
                    email,
                    phone,
                    address
                })
            });

        localStorage.setItem(
            'socCustomer',
            JSON.stringify(createdCustomer)
        );

        result.textContent =
            `Customer ready: ${createdCustomer.name} ` +
            `(${createdCustomer.id})`;

        renderCustomer();
        await loadCart();

    } catch (error) {
        result.textContent = error.message;
    }
}

function renderCustomer() {
    const selectedCustomer = customer();

    const customerDisplay =
        document.querySelector('#activeCustomer');

    if (selectedCustomer) {
        customerDisplay.textContent =
            `Buying as ${selectedCustomer.name} · ` +
            `Delivery to ${selectedCustomer.address}`;
    } else {
        customerDisplay.textContent =
            'Register before adding products to your cart.';
    }
}

async function addToCart(productId) {
    const selectedCustomer = customer();

    if (!selectedCustomer) {
        alert('Please register customer details first');

        document
            .querySelector('#activeCustomer')
            .scrollIntoView({
                behavior: 'smooth'
            });

        return;
    }

    const product = productCache.find(
        item => item.id === productId
    );

    if (!product) {
        alert('Product not found');
        return;
    }

    if (product.stock < 1) {
        alert('Product is out of stock');
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
        alert('Please register customer details first');

        document
            .querySelector('#activeCustomer')
            .scrollIntoView({
                behavior: 'smooth'
            });

        return;
    }

    const product = productCache.find(
        item => item.id === productId
    );

    if (!product) {
        alert('Product not found');
        return;
    }

    if (product.stock < 1) {
        alert('Product is out of stock');
        return;
    }

    const confirmed = confirm(
        `Buy ${product.name} for LKR ${product.price}?`
    );

    if (!confirmed) {
        return;
    }

    try {
        const order = await call('/orders', {
            method: 'POST',
            body: JSON.stringify({
                customerId: selectedCustomer.id,
                customerName: selectedCustomer.name,
                address: selectedCustomer.address,
                items: [
                    {
                        productId: product.id,
                        productName: product.name,
                        quantity: 1,
                        unitPrice: product.price
                    }
                ]
            })
        });

        // Refresh products to display reduced stock.
        await loadProducts();

        const openBillNow = confirm(
            `Purchase successful.\n` +
            `Total: LKR ${order.total}\n\n` +
            `Open printable bill?`
        );

        if (openBillNow) {
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
            '<p>Register customer details before using the cart.</p>';

        updateCartSummary();
        return;
    }

    try {
        currentCart = await call(
            `/carts/${selectedCustomer.id}`
        );

        if (!currentCart.items.length) {
            cartContainer.innerHTML =
                '<p>Your cart is empty.</p>';
        } else {
            cartContainer.innerHTML =
                currentCart.items.map(item => `
                    <div class="cart-row">
                        <strong>
                            ${item.productName}
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
        cartContainer.innerHTML = `
            <pre>${error.message}</pre>
        `;
    }
}

function updateCartSummary() {
    const items = currentCart?.items || [];

    const totalQuantity = items.reduce(
        (total, item) =>
            total + item.quantity,
        0
    );

    document.querySelector('#cartCount').textContent =
        totalQuantity;

    document.querySelector('#cartTotal').textContent =
        `Total: LKR ${currentCart?.total || 0}`;
}

async function removeFromCart(productId) {
    const selectedCustomer = customer();

    if (!selectedCustomer) {
        return;
    }

    try {
        currentCart = await call(
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
        alert('Please register customer details first');
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

        // Clear cart only after order succeeds.
        await call(`/carts/${selectedCustomer.id}`, {
            method: 'DELETE'
        });

        result.textContent =
            `Order placed successfully\n` +
            `Order: ${order.id}\n` +
            `Total: LKR ${order.total}`;

        await loadCart();

        // Reload product quantities.
        await loadProducts();

        const openBillNow = confirm(
            'Order placed successfully. ' +
            'Open printable bill now?'
        );

        if (openBillNow) {
            openBill(order.id);
        }

    } catch (error) {
        result.textContent = error.message;
    }
}

async function addProduct() {
    const name =
        document.querySelector('#pname').value.trim();

    const description =
        document
            .querySelector('#pdescription')
            .value
            .trim();

    const price =
        Number(
            document.querySelector('#pprice').value
        );

    const stock =
        Number(
            document.querySelector('#pstock').value
        );

    const imageUrl =
        document.querySelector('#pimage').value.trim();

    if (
        !name ||
        !description ||
        price <= 0 ||
        stock < 0
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

        alert('Product created');

        document.querySelector('#pname').value = '';
        document.querySelector('#pdescription').value = '';
        document.querySelector('#pprice').value = '';
        document.querySelector('#pstock').value = '';
        document.querySelector('#pimage').value = '';

        await loadProducts();

    } catch (error) {
        alert(error.message);
    }
}

async function loadOrders() {
    const orderContainer =
        document.querySelector('#orders');

    try {
        const orderList = await call('/orders');

        orderContainer.innerHTML =
            orderList.map(order => `
                <p>
                    <b>${order.id}</b>
                    — ${order.customerName}
                    — LKR ${order.total}
                    — ${order.status}

                    <button
                        onclick="openBill('${order.id}')"
                    >
                        Open bill
                    </button>
                </p>
            `).join('') ||
            '<p>No orders yet.</p>';

    } catch (error) {
        orderContainer.innerHTML = `
            <pre>${error.message}</pre>
        `;
    }
}

async function openBill(orderId) {
    try {
        const billHtml = await call(
            `/orders/${orderId}/bill`
        );

        const billWindow = window.open();

        if (!billWindow) {
            alert(
                'Please allow pop-ups to open the bill'
            );
            return;
        }

        billWindow.document.write(billHtml);
        billWindow.document.close();

    } catch (error) {
        alert(error.message);
    }
}

// Automatically load the site.
// Nginx adds the API key securely.
renderCustomer();
loadProducts();
loadCart();