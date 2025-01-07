// 全局变量
let isConnected = false;
const API_BASE_URL = '/api/kafka';
let currentDataSource = null;
let currentTopic = null;
let deleteModal = null;
let deleteSourceId = null;

// 初始化函数
$(document).ready(function() {
    initializeApp();
    setupEventListeners();
});

function initializeApp() {
    // 全局 Ajax 设置
    $.ajaxSetup({
        cache: false,
        headers: {
            'Cache-Control': 'no-cache',
            'Pragma': 'no-cache'
        }
    });

    // Toastr 配置
    toastr.options = {
        closeButton: true,
        progressBar: true,
        positionClass: "toast-top-right",
        timeOut: 3000
    };

    // 初始状态设置
    $('#sendForm').find('button[type="submit"]').prop('disabled', true);
    loadDataSources();

    // 添加加载动画
    $(document).ajaxStart(function() {
        $('#messageContent').addClass('loading');
    }).ajaxStop(function() {
        $('#messageContent').removeClass('loading');
    });
}

function setupEventListeners() {
    // 初始化模态框
    deleteModal = new bootstrap.Modal(document.getElementById('deleteConfirmModal'));
    const createTopicModal = new bootstrap.Modal(document.getElementById('createTopicModal'));
    const addSourceModal = new bootstrap.Modal(document.getElementById('addSourceModal'));

    // 添加数据源相关事件
    setupDataSourceEvents(addSourceModal);
    
    // Topic相关事件
    setupTopicEvents(createTopicModal);
    
    // 消息相关事件
    setupMessageEvents();
    
    // 过滤相关事件
    setupFilterEvents();
}

function setupDataSourceEvents(addSourceModal) {
    // 添加数据源按钮点击事件
    $('#addSourceBtn').on('click', function() {
        $('#sourceName').val('');
        $('#bootstrapServers').val('');
        addSourceModal.show();
    });

    // 添加数据源表单提交
    $('#kafkaForm').on('submit', function(e) {
        e.preventDefault();
        const dataSource = {
            name: $('#sourceName').val(),
            bootstrapServers: $('#bootstrapServers').val(),
            connected: false
        };

        $.ajax({
            url: `${API_BASE_URL}/datasource`,
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(dataSource),
            success: function(response) {
                addSourceModal.hide();
                loadDataSources();
                toastr.success('数据源添加成功');
            },
            error: function(xhr, status, error) {
                toastr.error('添加数据源失败: ' + (xhr.responseJSON?.error || error));
            }
        });
    });

    // 确认删除数据源
    $('#confirmDeleteBtn').on('click', function() {
        if (deleteSourceId) {
            confirmDelete(deleteSourceId);
            deleteModal.hide();
        }
    });
}

function setupTopicEvents(createTopicModal) {
    // 创建 Topic 按钮点击事件
    $('#createTopicBtn').on('click', function() {
        if (!currentDataSource) {
            toastr.warning('请先选择数据源');
            return;
        }
        $('#topicName').val('');
        $('#numPartitions').val('1');
        $('#replicationFactor').val('1');
        createTopicModal.show();
    });

    // 创建 Topic 表单提交
    $('#createTopicForm').on('submit', function(e) {
        e.preventDefault();
        const topicData = {
            name: $('#topicName').val(),
            numPartitions: parseInt($('#numPartitions').val()),
            replicationFactor: parseInt($('#replicationFactor').val())
        };

        $.ajax({
            url: `${API_BASE_URL}/datasource/${currentDataSource.id}/topics`,
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(topicData),
            success: function() {
                createTopicModal.hide();
                loadTopics(currentDataSource.id);
                toastr.success('Topic创建成功');
            },
            error: function(xhr, status, error) {
                toastr.error('Topic创建失败: ' + (xhr.responseJSON?.error || error));
            }
        });
    });
}

function setupMessageEvents() {
    // 刷新按钮点击事件
    $('#refreshBtn').on('click', function() {
        if (currentTopic) {
            loadLatestMessages(currentTopic);
        }
    });

    // 发送消息表单提交
    $('#sendForm').on('submit', function(e) {
        e.preventDefault();
        if (!currentDataSource || !currentTopic) {
            toastr.warning('请先选择数据源和Topic');
            return;
        }
        const value = $('#messageValue').val();
        if (!value) {
            toastr.warning('消息内容不能为空');
            return;
        }
        sendMessage(currentTopic, value);
    });
}

function setupFilterEvents() {
    // Topic 过滤
    $('#topicFilter').on('input', function() {
        const filterText = $(this).val().toLowerCase();
        $('.topic-item').each(function() {
            const topicName = $(this).text().trim().toLowerCase();
            if (topicName.includes(filterText)) {
                $(this).removeClass('filtered');
            } else {
                $(this).addClass('filtered');
            }
        });
    });

    // 消息过滤
    $('#messageFilter').on('input', function() {
        const filterText = $(this).val().toLowerCase();
        $('.message-item').each(function() {
            const messageContent = $(this).find('.message-content-wrapper').text().toLowerCase();
            if (messageContent.includes(filterText)) {
                $(this).removeClass('filtered');
            } else {
                $(this).addClass('filtered');
            }
        });
    });
}

function bindDataSourceEvents() {
    $('.datasource-item').on('click', function() {
        const id = $(this).data('id');
        const servers = $(this).data('servers');
        selectDataSource(id, servers);
    });
}

function bindTopicEvents() {
    $('.topic-item').on('click', function() {
        const topic = $(this).data('topic');
        selectTopic(topic);
    });
}

function selectDataSource(id, servers) {
    const selectedSource = $('#dataSourceList').find(`[data-id="${id}"]`);
    const sourceName = selectedSource.find('.fw-medium').text();
    currentDataSource = { id, bootstrapServers: servers };
    $('#dataSourceList .datasource-item').removeClass('active animate__animated animate__pulse');
    const $selected = $(`#dataSourceList [data-id="${id}"]`);
    $selected.addClass('active animate__animated animate__pulse');
    
    $('#currentSource').text(sourceName);
    
    $('#messageContent').empty();
    $('#messageFilter').val('');
    currentTopic = null;
    $('#sendForm').find('button[type="submit"]').prop('disabled', true);
    
    loadTopics(id);
    updateConnectionStatus(true);
}

function selectTopic(topic) {
    $('#messageFilter').val('');
    currentTopic = topic;
    $('#topicList .topic-item').removeClass('active');
    $(`#topicList [data-topic="${topic}"]`).addClass('active');
    $('#sendForm').find('button[type="submit"]').prop('disabled', false);
    loadLatestMessages(topic);
}

function loadDataSources() {
    $.ajax({
        url: `${API_BASE_URL}/datasource`,
        method: 'GET',
        cache: false,
        timeout: 5000,
        success: function(response) {
            const $list = $('#dataSourceList');
            $list.empty();
            
            if (!currentDataSource) {
                $('#currentSource').text('未选择数据源');
                updateConnectionStatus(false);
            }
            
            response.forEach(ds => {
                $list.append(`
                    <div class="list-group-item list-group-item-action datasource-item" 
                         data-id="${ds.id}" data-servers="${ds.bootstrapServers}">
                        <div class="datasource-info">
                            <i class="bi bi-hdd-network"></i>
                            <div>
                                <div class="fw-medium">${ds.name}</div>
                                <div class="text-muted small">${ds.bootstrapServers}</div>
                            </div>
                        </div>
                        <button class="btn btn-outline-danger btn-sm delete-btn" 
                                onclick="event.stopPropagation(); deleteDataSource(${ds.id});">
                            <i class="bi bi-trash"></i>
                        </button>
                    </div>
                `);
                
                if (currentDataSource && currentDataSource.id === ds.id) {
                    $(`#dataSourceList [data-id="${ds.id}"]`).addClass('active');
                }
            });
            bindDataSourceEvents();
        },
        error: function(xhr, status, error) {
            console.error('Failed to load data sources:', error);
            $('#currentSource').text('未选择数据源');
            updateConnectionStatus(false);
        }
    });
}

function loadTopics(dataSourceId) {
    $('#topicList').html(`
        <div class="loading">
            <div class="loading-text">加载中...</div>
            <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
            </div>
        </div>`);
    $.ajax({
        url: `${API_BASE_URL}/datasource/${dataSourceId}/topics`,
        method: 'GET',
        cache: false,
        timeout: 5000,
        success: function(response) {
            const $list = $('#topicList');
            $list.empty();
            const filterText = $('#topicFilter').val().toLowerCase();
            response.forEach(topic => {
                const isFiltered = filterText && !topic.toLowerCase().includes(filterText);
                $list.append(`
                    <div class="topic-item animate__animated animate__fadeIn ${isFiltered ? 'filtered' : ''}" 
                        data-topic="${topic}">
                        <button class="btn btn-outline-danger btn-sm delete-btn" 
                                onclick="event.stopPropagation(); deleteTopic('${topic}');"
                                title="删除Topic">
                            <i class="bi bi-trash"></i>
                        </button>
                        <div class="topic-item-content">
                            <i class="bi bi-chat-text"></i>
                            <div class="topic-name" title="${topic}">${topic}</div>
                        </div>
                    </div>
                `);
            });
            bindTopicEvents();
        }
    });
}

function loadLatestMessages(topic) {
    const loadingHtml = `
        <div class="loading">
            <div class="loading-text">加载中...</div>
            <div class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
            </div>
        </div>`;
    $('#messageContent').html(loadingHtml);

    setTimeout(() => {
        $.ajax({
            url: `${API_BASE_URL}/datasource/${currentDataSource.id}/topics/${topic}/messages`,
            method: 'GET',
            cache: false,
            data: { 
                limit: 100,
                _: new Date().getTime()
            },
            timeout: 10000,
            success: function(response) {
                if (response.length === 0) {
                    setTimeout(() => {
                        $.ajax({
                            url: `${API_BASE_URL}/datasource/${currentDataSource.id}/topics/${topic}/messages`,
                            method: 'GET',
                            cache: false,
                            data: { limit: 100 },
                            success: function(retryResponse) {
                                if (retryResponse.length === 0) {
                                    $('#messageContent').html(`
                                        <div class="text-center text-muted p-4">
                                            <i class="bi bi-inbox fs-2 mb-2"></i>
                                            <div>暂无消息</div>
                                            <button class="btn btn-outline-primary btn-sm mt-2" onclick="loadLatestMessages('${topic}')">
                                                <i class="bi bi-arrow-clockwise"></i> 重新加载
                                            </button>
                                        </div>
                                    `);
                                } else {
                                    renderMessages(retryResponse);
                                }
                            },
                            error: function() {
                                renderMessages(response);
                            }
                        });
                    }, 1000);
                    return;
                }
                renderMessages(response);
            },
            error: function(xhr, status, error) {
                $('#messageContent').html(`
                    <div class="alert alert-danger m-3">
                        <div class="mb-2">加载消息失败: ${xhr.responseJSON?.error || error}</div>
                        <button class="btn btn-outline-danger btn-sm" onclick="loadLatestMessages('${topic}')">
                            <i class="bi bi-arrow-clockwise"></i> 重试
                        </button>
                    </div>
                `);
            }
        });
    }, 500);
}

function renderMessages(messages) {
    const messagesHtml = messages.map(msg => {
        const date = new Date(msg.timestamp).toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false
        });
        let formattedValue = msg.value;
        try {
            const jsonObj = JSON.parse(msg.value);
            formattedValue = JSON.stringify(jsonObj, null, 2);
        } catch (e) {
            // 如果不是 JSON 格式，保持原样
        }
        return `
            <div class="message-item animate__animated animate__fadeIn">
                <div class="message-header">
                    <span>
                        <i class="bi bi-clock"></i>
                        ${date}
                    </span>
                    <span>
                        <i class="bi bi-diagram-2"></i>
                        分区: ${msg.partition}
                        <i class="bi bi-hash ms-3"></i>
                        偏移量: ${msg.offset}
                    </span>
                </div>
                <div class="message-content-wrapper">
                    <pre class="mb-0">${formattedValue}</pre>
                </div>
            </div>`;
    }).join('');
    $('#messageContent').html(messagesHtml);

    const filterText = $('#messageFilter').val().toLowerCase();
    if (filterText) {
        $('.message-item').each(function() {
            const messageContent = $(this).find('.message-content-wrapper').text().toLowerCase();
            if (!messageContent.includes(filterText)) {
                $(this).addClass('filtered');
            }
        });
    }
    $('#messageContent').scrollTop($('#messageContent')[0].scrollHeight);
}

function sendMessage(topic, message) {
    const messageData = {
        partition: $('#partition').val() ? parseInt($('#partition').val()) : null,
        key: $('#messageKey').val() || null,
        value: message
    };

    $.ajax({
        url: `${API_BASE_URL}/datasource/${currentDataSource.id}/topics/${topic}/messages`,
        method: 'POST',
        contentType: 'application/json;charset=UTF-8',
        data: JSON.stringify(messageData),
        success: function() {
            $('#partition').val('');
            $('#messageKey').val('');
            $('#messageValue').val('');
            toastr.success('消息发送成功');
            loadLatestMessages(topic);
        },
        error: function(xhr, status, error) {
            toastr.error('消息发送失败: ' + (xhr.responseJSON?.error || error));
            console.error('Failed to send message:', error);
        }
    });
}

function deleteDataSource(id) {
    deleteSourceId = id;
    deleteModal.show();
}

function deleteTopic(topic) {
    if (!currentDataSource) return;
    
    const modal = new bootstrap.Modal(document.getElementById('deleteTopicModal'));
    $('#deleteTopicName').text(topic);
    $('#confirmDeleteTopicBtn').off('click').on('click', function() {
        $.ajax({
            url: `${API_BASE_URL}/datasource/${currentDataSource.id}/topics/${topic}`,
            method: 'DELETE',
            success: function() {
                modal.hide();
                if (currentTopic === topic) {
                    currentTopic = null;
                    $('#messageContent').empty();
                    $('#sendForm').find('button[type="submit"]').prop('disabled', true);
                }
                loadTopics(currentDataSource.id);
                toastr.success('Topic删除成功');
            },
            error: function(xhr, status, error) {
                toastr.error('Topic删除失败: ' + (xhr.responseJSON?.error || error));
            }
        });
    });
    modal.show();
}

function confirmDelete(id) {
    $.ajax({
        url: `${API_BASE_URL}/datasource/${id}`,
        method: 'DELETE',
        success: function() {
            if (currentDataSource && currentDataSource.id === id) {
                currentDataSource = null;
                currentTopic = null;
                $('#topicList').empty();
                $('#messageContent').empty();
                $('#sendForm').find('button[type="submit"]').prop('disabled', true);
                $('#currentSource').text('未选择数据源');
                updateConnectionStatus(false);
            }
            loadDataSources();
            toastr.success('数据源删除成功');
            deleteSourceId = null;
        }
    });
}

function updateConnectionStatus(connected) {
    isConnected = connected;
    const $status = $('#connectionStatus');
    if (connected) {
        $status.removeClass('bg-secondary').addClass('bg-success').text('已连接');
    } else {
        $status.removeClass('bg-success').addClass('bg-secondary').text('未连接');
    }
}