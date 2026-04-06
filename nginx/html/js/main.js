document.addEventListener("DOMContentLoaded", function() {
    // 页面加载完成后，从后端动态拉取数据
    // 假设商品 ID 为 1
    fetchProductDetail(1);
});

function fetchProductDetail(productId) {
    // 调用 Nginx 代理的后端 API
    fetch('/api/product/' + productId)
        .then(response => {
            // 获取并显示 HTTP 头中自定义的端口标识，以验证负载均衡
            const servedBy = response.headers.get('X-Served-By');
            if (servedBy) {
                document.getElementById('server-port').innerText = servedBy;
            }
            return response.json();
        })
        .then(result => {
            if (result.code === 200 && result.data) {
                const product = result.data;
                document.getElementById('product-name').innerText = product.productName;
                document.getElementById('product-price').innerText = product.price;
                document.getElementById('product-stock').innerText = product.stock;
                document.getElementById('product-desc').innerText = "限时抢购，手慢无！";
            } else {
                document.getElementById('product-name').innerText = "未找到该商品";
                document.getElementById('product-price').innerText = "0.00";
                document.getElementById('product-stock').innerText = "0";
            }
        })
        .catch(error => {
            console.error('获取商品信息失败:', error);
            document.getElementById('product-name').innerText = "服务开小差了~";
        });
}