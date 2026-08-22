const api='/api';
const headers=()=>({'Content-Type':'application/json','X-API-KEY':localStorage.getItem('socKey')||''});
function saveKey(){localStorage.setItem('socKey',document.querySelector('#apiKey').value);loadProducts()}
function show(id){document.querySelector('#shop').classList.toggle('hidden',id!=='shop');document.querySelector('#admin').classList.toggle('hidden',id!=='admin')}
async function call(path,opt={}){const r=await fetch(api+path,{...opt,headers:{...headers(),...(opt.headers||{})}});const text=await r.text();if(!r.ok)throw new Error(text);return (r.headers.get('content-type')||'').includes('json')?JSON.parse(text):text}
async function loadProducts(){try{const items=await call('/products');products.innerHTML=items.map(p=>`<article class="product"><img src="${p.imageUrl||'https://placehold.co/600x400?text=Product'}" alt=""><div><h3>${p.name}</h3><p>${p.description}</p><p class="price">LKR ${p.price}</p><small>${p.stock} in stock</small></div></article>`).join('')||'<p>No products yet.</p>'}catch(e){products.innerHTML=`<pre>${e.message}</pre>`}}
async function createCustomer(){try{customerResult.textContent=JSON.stringify(await call('/customers',{method:'POST',body:JSON.stringify({name:cname.value,email:cemail.value,phone:cphone.value,address:caddress.value})}),null,2)}catch(e){customerResult.textContent=e.message}}
async function addProduct(){try{await call('/products',{method:'POST',body:JSON.stringify({name:pname.value,description:pdescription.value,price:+pprice.value,stock:+pstock.value,imageUrl:pimage.value})});alert('Product created');loadProducts()}catch(e){alert(e.message)}}
async function createKey(){try{keyResult.textContent=JSON.stringify(await call('/security/keys',{method:'POST',body:JSON.stringify({name:kname.value,role:krole.value})}),null,2)}catch(e){keyResult.textContent=e.message}}
async function loadOrders(){try{const os=await call('/orders');orders.innerHTML=os.map(o=>`<p><b>${o.id}</b> — ${o.customerName} — LKR ${o.total} — ${o.status} <button onclick="openBill('${o.id}')">Open bill</button></p>`).join('')}catch(e){orders.innerHTML=`<pre>${e.message}</pre>`}}
async function openBill(id){try{const html=await call('/orders/'+id+'/bill');const w=open();w.document.write(html);w.document.close()}catch(e){alert(e.message)}}
document.querySelector('#apiKey').value=localStorage.getItem('socKey')||'';
