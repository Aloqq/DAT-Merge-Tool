const state = {
    format: 'line',
    records: [],
    visibleStart: 0,
    visibleEnd: 50, // Начальное количество видимых записей
    itemHeight: 150, // Примерная высота одной карточки записи (обновлено под новый дизайн)
    searchQuery: '' // Текущий поисковый запрос
};

const elements = {
    uploadBtn: document.getElementById('uploadBtn'),
    exportBtn: document.getElementById('exportBtn'),
    oldInput: document.getElementById('oldFile'),
    newInput: document.getElementById('newFile'),
    statusText: document.getElementById('statusText'),
    recordsContainer: document.getElementById('recordsContainer'),
    recordsWrapper: document.querySelector('.records-wrapper'),
    showOnlyChanges: document.getElementById('showOnlyChanges'),
    showOnlyChangedFields: document.getElementById('showOnlyChangedFields'),
    massActionsBtn: document.getElementById('massActionsBtn'),
    massActionsPanel: document.getElementById('massActionsPanel'),
    massActionsList: document.getElementById('massActionsList'),
    closeMassActions: document.getElementById('closeMassActions'),
    statsPanel: document.getElementById('statsPanel'),
    statsContent: document.getElementById('statsContent'),
    toggleStats: document.getElementById('toggleStats'),
    searchInput: document.getElementById('searchInput'),
    searchBtn: document.getElementById('searchBtn'),
    clearSearch: document.getElementById('clearSearch'),
    recentFilesPanel: document.getElementById('recentFilesPanel'),
    recentFilesList: document.getElementById('recentFilesList'),
    toggleRecentFiles: document.getElementById('toggleRecentFiles')
};

// IndexedDB для хранения файлов
let db = null;
const DB_NAME = 'DiffChangeDB';
const DB_VERSION = 1;
const STORE_NAME = 'files';

// Инициализация IndexedDB
function initDB() {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open(DB_NAME, DB_VERSION);
        
        request.onerror = () => reject(request.error);
        request.onsuccess = () => {
            db = request.result;
            resolve(db);
        };
        
        request.onupgradeneeded = (event) => {
            const database = event.target.result;
            if (!database.objectStoreNames.contains(STORE_NAME)) {
                database.createObjectStore(STORE_NAME, { keyPath: 'id', autoIncrement: true });
            }
        };
    });
}

// Сохранение файла в IndexedDB
async function saveFile(file, type) {
    if (!db) await initDB();
    
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = async (e) => {
            const fileData = {
                name: file.name,
                type: type, // 'old' или 'new'
                size: file.size,
                lastModified: file.lastModified,
                data: e.target.result, // ArrayBuffer
                timestamp: Date.now()
            };
            
            const transaction = db.transaction([STORE_NAME], 'readwrite');
            const store = transaction.objectStore(STORE_NAME);
            
            // Удаляем старые файлы того же типа (оставляем только последние 10)
            const getAllRequest = store.getAll();
            getAllRequest.onsuccess = () => {
                const allFiles = getAllRequest.result;
                const sameTypeFiles = allFiles
                    .filter(f => f.type === type)
                    .sort((a, b) => b.timestamp - a.timestamp);
                
                // Удаляем старые файлы, оставляем только последние 9
                if (sameTypeFiles.length >= 10) {
                    const toDelete = sameTypeFiles.slice(9);
                    toDelete.forEach(file => {
                        store.delete(file.id);
                    });
                }
                
                // Добавляем новый файл
                const addRequest = store.add(fileData);
                addRequest.onsuccess = () => resolve(addRequest.result);
                addRequest.onerror = () => reject(addRequest.error);
            };
        };
        reader.onerror = () => reject(reader.error);
        reader.readAsArrayBuffer(file);
    });
}

// Получение всех сохраненных файлов
async function getSavedFiles() {
    if (!db) await initDB();
    
    return new Promise((resolve, reject) => {
        const transaction = db.transaction([STORE_NAME], 'readonly');
        const store = transaction.objectStore(STORE_NAME);
        const request = store.getAll();
        
        request.onsuccess = () => {
            const files = request.result
                .sort((a, b) => b.timestamp - a.timestamp)
                .slice(0, 20); // Максимум 20 последних файлов
            resolve(files);
        };
        request.onerror = () => reject(request.error);
    });
}

// Удаление файла
async function deleteFile(fileId) {
    if (!db) await initDB();
    
    return new Promise((resolve, reject) => {
        const transaction = db.transaction([STORE_NAME], 'readwrite');
        const store = transaction.objectStore(STORE_NAME);
        const request = store.delete(fileId);
        
        request.onsuccess = () => resolve();
        request.onerror = () => reject(request.error);
    });
}

// Восстановление файла из IndexedDB
async function restoreFile(fileData) {
    const blob = new Blob([fileData.data], { type: 'text/plain' });
    const file = new File([blob], fileData.name, {
        lastModified: fileData.lastModified,
        type: 'text/plain'
    });
    return file;
}

// Отображение списка последних файлов
async function renderRecentFiles() {
    if (!elements.recentFilesList) return;
    
    try {
        const files = await getSavedFiles();
        
        if (files.length === 0) {
            elements.recentFilesPanel.style.display = 'none';
            return;
        }
        
        elements.recentFilesPanel.style.display = 'block';
        
        // Группируем файлы по парам (old/new)
        const filePairs = [];
        const oldFiles = files.filter(f => f.type === 'old');
        const newFiles = files.filter(f => f.type === 'new');
        
        // Создаем пары из последних файлов
        const maxPairs = Math.max(oldFiles.length, newFiles.length);
        for (let i = 0; i < maxPairs && i < 5; i++) {
            const oldFile = oldFiles[i];
            const newFile = newFiles[i];
            if (oldFile || newFile) {
                filePairs.push({ old: oldFile, new: newFile, id: i });
            }
        }
        
        elements.recentFilesList.innerHTML = '';
        
        filePairs.forEach(pair => {
            const item = document.createElement('div');
            item.className = 'recent-file-item';
            
            const fileInfo = document.createElement('div');
            fileInfo.className = 'recent-file-info';
            
            if (pair.old) {
                const oldDiv = document.createElement('div');
                oldDiv.className = 'recent-file-entry';
                oldDiv.innerHTML = `
                    <span class="file-type">OLD:</span>
                    <span class="file-name" title="${pair.old.name}">${escapeHtml(pair.old.name)}</span>
                    <button class="btn-icon delete-file" data-file-id="${pair.old.id}" data-file-type="old" title="Удалить">✕</button>
                `;
                fileInfo.appendChild(oldDiv);
            }
            
            if (pair.new) {
                const newDiv = document.createElement('div');
                newDiv.className = 'recent-file-entry';
                newDiv.innerHTML = `
                    <span class="file-type">NEW:</span>
                    <span class="file-name" title="${pair.new.name}">${escapeHtml(pair.new.name)}</span>
                    <button class="btn-icon delete-file" data-file-id="${pair.new.id}" data-file-type="new" title="Удалить">✕</button>
                `;
                fileInfo.appendChild(newDiv);
            }
            
            const useBtn = document.createElement('button');
            useBtn.className = 'btn tiny';
            useBtn.textContent = 'Использовать';
            useBtn.onclick = async () => {
                if (pair.old) {
                    const oldFile = await restoreFile(pair.old);
                    const dataTransfer = new DataTransfer();
                    dataTransfer.items.add(oldFile);
                    elements.oldInput.files = dataTransfer.files;
                }
                if (pair.new) {
                    const newFile = await restoreFile(pair.new);
                    const dataTransfer = new DataTransfer();
                    dataTransfer.items.add(newFile);
                    elements.newInput.files = dataTransfer.files;
                }
                setStatus('Файлы восстановлены. Нажмите "Загрузить и сравнить"', false);
            };
            
            item.appendChild(fileInfo);
            item.appendChild(useBtn);
            elements.recentFilesList.appendChild(item);
        });
        
        // Обработчики удаления
        elements.recentFilesList.querySelectorAll('.delete-file').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                e.stopPropagation();
                const fileId = parseInt(btn.dataset.fileId);
                try {
                    await deleteFile(fileId);
                    await renderRecentFiles();
                    setStatus('Файл удален', false);
                } catch (error) {
                    console.error('Ошибка удаления файла:', error);
                    setStatus('Ошибка удаления файла', true);
                }
            });
        });
        
    } catch (error) {
        console.error('Ошибка загрузки последних файлов:', error);
    }
}

elements.uploadBtn.addEventListener('click', handleUpload);
elements.exportBtn.addEventListener('click', handleExport);
elements.recordsContainer.addEventListener('click', handleRecordsClick);
elements.recordsContainer.addEventListener('input', handleMergedInput);
elements.recordsContainer.addEventListener('blur', handleMergedInput, true);
if (elements.showOnlyChanges) {
    elements.showOnlyChanges.addEventListener('change', () => {
        // Сбрасываем виртуализацию при изменении фильтра
        state.visibleStart = 0;
        const showOnlyChanges = elements.showOnlyChanges.checked;
        const visibleRecords = showOnlyChanges 
            ? state.records.filter(r => r.hasChanges)
            : state.records;
        state.visibleEnd = Math.min(20, visibleRecords.length);
        renderRecords();
    });
}
if (elements.showOnlyChangedFields) {
    elements.showOnlyChangedFields.addEventListener('change', () => {
        // Сбрасываем виртуализацию при изменении фильтра
        state.visibleStart = 0;
        state.visibleEnd = Math.min(20, state.records.length);
        // Перерисовываем записи, так как фильтр влияет на видимость записей
        renderRecords();
    });
}
if (elements.massActionsBtn) {
    elements.massActionsBtn.addEventListener('click', showMassActions);
}
if (elements.closeMassActions) {
    elements.closeMassActions.addEventListener('click', () => {
        elements.massActionsPanel.style.display = 'none';
    });
}
if (elements.toggleStats && elements.statsContent) {
    // Инициализация: по умолчанию контент виден
    let isCollapsed = false;
    
    // Восстанавливаем состояние из localStorage
    const savedState = localStorage.getItem('statsPanelCollapsed');
    if (savedState === 'true') {
        isCollapsed = true;
        elements.statsContent.style.display = 'none';
        elements.toggleStats.textContent = '+';
        document.body.classList.add('stats-collapsed');
    } else {
        elements.statsContent.style.display = 'block';
        elements.toggleStats.textContent = '−';
        document.body.classList.remove('stats-collapsed');
    }
    
    elements.toggleStats.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        
        isCollapsed = !isCollapsed;
        
        if (isCollapsed) {
            elements.statsContent.style.display = 'none';
            elements.toggleStats.textContent = '+';
            document.body.classList.add('stats-collapsed');
        } else {
            elements.statsContent.style.display = 'block';
            elements.toggleStats.textContent = '−';
            document.body.classList.remove('stats-collapsed');
        }
        
        // Сохраняем состояние
        localStorage.setItem('statsPanelCollapsed', Boolean(isCollapsed).toString());
    });
}

// Функция выполнения поиска
function performSearch() {
    console.log('performSearch вызвана');
    
    if (!state.records || state.records.length === 0) {
        const message = 'Нет данных для поиска. Сначала загрузите файлы.';
        console.warn(message);
        setStatus(message, true);
        return;
    }
    
    if (!elements.searchInput) {
        console.error('Поле поиска не найдено');
        return;
    }
    
    const query = elements.searchInput.value.trim();
    console.log('Поисковый запрос:', query);
    
    state.searchQuery = query.toLowerCase();
    
    // Показываем/скрываем кнопку очистки
    if (elements.clearSearch) {
        elements.clearSearch.style.display = state.searchQuery ? 'block' : 'none';
    }
    
    state.visibleStart = 0;
    state.visibleEnd = Math.min(20, state.records.length);
    
    // Визуальная обратная связь для кнопки
    if (elements.searchBtn) {
        elements.searchBtn.style.transform = 'scale(0.9)';
        elements.searchBtn.style.opacity = '0.7';
        setTimeout(() => {
            if (elements.searchBtn) {
                elements.searchBtn.style.transform = '';
                elements.searchBtn.style.opacity = '';
            }
        }, 150);
    }
    
    // Выполняем рендеринг
    renderRecords();
    
    // Показываем статус поиска
    if (state.searchQuery) {
        const showOnlyChanges = elements.showOnlyChanges?.checked ?? false;
        const showOnlyChangedFields = elements.showOnlyChangedFields?.checked ?? false;
        
        let visibleRecords = showOnlyChanges 
            ? state.records.filter(r => r.hasChanges)
            : state.records;
        
        // Если включен фильтр "Показать только измененные поля", 
        // дополнительно фильтруем записи, в которых нет измененных полей
        if (showOnlyChangedFields) {
            visibleRecords = visibleRecords.filter(record => {
                return record.fields.some(field => 
                    field.status === 'changed' || field.status === 'added' || field.status === 'removed'
                );
            });
        }
        
        const foundCount = visibleRecords.filter(record => matchesSearch(record, state.searchQuery)).length;
        
        console.log(`Найдено записей: ${foundCount} из ${visibleRecords.length}`);
        
        if (foundCount === 0) {
            setStatus(`По запросу "${query}" ничего не найдено`, true);
        } else {
            setStatus(`Найдено записей: ${foundCount} из ${visibleRecords.length}`, false);
        }
    } else {
        setStatus('Поиск очищен', false);
    }
}

// Обработка поиска
if (elements.searchInput) {
    let searchTimeout;
    
    // Поиск при вводе (с дебаунсом)
    elements.searchInput.addEventListener('input', (e) => {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            performSearch();
        }, 300); // Дебаунс 300мс
    });
    
    // Поиск при нажатии Enter
    elements.searchInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            clearTimeout(searchTimeout);
            performSearch();
        } else if (e.key === 'Escape') {
            elements.searchInput.value = '';
            state.searchQuery = '';
            elements.clearSearch.style.display = 'none';
            state.visibleStart = 0;
            state.visibleEnd = Math.min(20, state.records.length);
            renderRecords();
        }
    });
}

// Кнопка поиска
if (elements.searchBtn) {
    elements.searchBtn.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        console.log('Кнопка поиска нажата, запрос:', elements.searchInput?.value);
        
        if (!elements.searchInput) {
            console.error('Поле поиска не найдено');
            setStatus('Ошибка: поле поиска не найдено', true);
            return;
        }
        
        performSearch();
        
        // Фокус на поле ввода после поиска
        setTimeout(() => {
            if (elements.searchInput) {
                elements.searchInput.focus();
            }
        }, 100);
    });
    
    // Предотвращаем стандартное поведение при mousedown
    elements.searchBtn.addEventListener('mousedown', (e) => {
        e.preventDefault();
    });
} else {
    console.warn('Кнопка поиска не найдена в DOM');
}

// Кнопка очистки поиска
if (elements.clearSearch) {
    elements.clearSearch.addEventListener('click', () => {
        elements.searchInput.value = '';
        state.searchQuery = '';
        elements.clearSearch.style.display = 'none';
        state.visibleStart = 0;
        state.visibleEnd = Math.min(20, state.records.length);
        renderRecords();
        elements.searchInput.focus();
    });
}

// Инициализация IndexedDB и загрузка последних файлов
if (elements.recentFilesPanel) {
    initDB().then(() => {
        renderRecentFiles();
    }).catch(error => {
        console.error('Ошибка инициализации IndexedDB:', error);
    });
    
    // Переключение видимости панели последних файлов
    if (elements.toggleRecentFiles) {
        let isCollapsed = false;
        const savedState = localStorage.getItem('recentFilesCollapsed');
        if (savedState === 'true') {
            isCollapsed = true;
            elements.recentFilesList.style.display = 'none';
            elements.toggleRecentFiles.textContent = '+';
        }
        
        elements.toggleRecentFiles.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            
            isCollapsed = !isCollapsed;
            
            if (isCollapsed) {
                elements.recentFilesList.style.display = 'none';
                elements.toggleRecentFiles.textContent = '+';
            } else {
                elements.recentFilesList.style.display = 'block';
                elements.toggleRecentFiles.textContent = '−';
            }
            
            localStorage.setItem('recentFilesCollapsed', Boolean(isCollapsed).toString());
        });
    }
}

function setStatus(message, isError = false) {
    elements.statusText.textContent = message || '';
    elements.statusText.style.color = isError ? '#ff7676' : '#b7b0b0';
}

async function handleUpload() {
    const oldFile = elements.oldInput.files[0];
    const newFile = elements.newInput.files[0];

    if (!oldFile || !newFile) {
        setStatus('Загрузите оба файла (OLD и NEW)', true);
        return;
    }

    // Сохраняем файлы в IndexedDB
    try {
        await saveFile(oldFile, 'old');
        await saveFile(newFile, 'new');
        await renderRecentFiles(); // Обновляем список
    } catch (error) {
        console.error('Ошибка сохранения файлов:', error);
        // Продолжаем работу даже если сохранение не удалось
    }

    const formData = new FormData();
    formData.append('old', oldFile);
    formData.append('new', newFile);

        setStatus('Загружаю и сравниваю...');
        elements.uploadBtn.disabled = true;
        elements.recordsContainer.innerHTML = '<div class="hint">Обработка файлов, пожалуйста подождите...</div>';

    try {
        console.log('Отправка запроса /upload...');
        const response = await fetch('/upload', {
            method: 'POST',
            body: formData
        });
        console.log('Получен ответ:', response.status, response.statusText);
        console.log('Content-Type:', response.headers.get('content-type'));
        
        if (!response.ok) {
            const text = await response.text();
            console.error('Ошибка ответа:', text);
            throw new Error(text || 'Ошибка загрузки');
        }
        
        const contentType = response.headers.get('content-type');
        if (!contentType || !contentType.includes('application/json')) {
            const text = await response.text();
            console.error('Неожиданный Content-Type. Ответ:', text.substring(0, 500));
            throw new Error('Сервер вернул не JSON. Content-Type: ' + contentType);
        }
        
        console.log('Парсинг JSON...');
        let data;
        try {
            const text = await response.text();
            console.log('Получен текст ответа, длина:', text.length, 'символов');
            console.log('Первые 500 символов:', text.substring(0, 500));
            if (!text || text.trim().length === 0) {
                throw new Error('Получен пустой ответ от сервера');
            }
            data = JSON.parse(text);
            console.log('JSON распарсен. Format:', data.format, 'Records count:', data.records?.length);
        } catch (parseError) {
            console.error('Ошибка парсинга JSON:', parseError);
            console.error('Стек:', parseError.stack);
            throw new Error('Не удалось распарсить JSON ответ: ' + parseError.message);
        }
        
        setStatus('Обработка данных...');
        // Используем requestAnimationFrame для неблокирующей обработки
        requestAnimationFrame(() => {
            applyDiffResponse(data);
            setStatus(`Найдено записей: ${state.records.length}. Формат: ${state.format}`);
            elements.exportBtn.disabled = false;
            if (elements.massActionsBtn) {
                elements.massActionsBtn.disabled = false;
            }
        });
    } catch (error) {
        console.error('Ошибка в handleUpload:', error);
        setStatus(error.message, true);
    } finally {
        elements.uploadBtn.disabled = false;
    }
}

function applyDiffResponse(payload) {
    console.log('applyDiffResponse вызвана с payload:', payload);
    state.format = payload.format || 'line';
    console.log('Установлен формат:', state.format);
    
    const recordsArray = payload.records || [];
    console.log('Обработка записей:', recordsArray.length);
    
    state.records = recordsArray.map((record, idx) => {
        if (idx < 3) {
            console.log(`Запись ${idx}:`, record);
        }
        const processedRecord = {
            ...record,
            deleted: record.deleted || false,
            fields: (record.fields || []).map(field => ({
                ...field,
                mergedValue: field.mergedValue ?? field.newValue ?? field.oldValue ?? '',
                deleted: field.deleted || false
            }))
        };
        
        // Проверяем, есть ли изменения в записи
        const hasChanges = processedRecord.fields.some(field => 
            field.status === 'changed' || field.status === 'added' || field.status === 'removed'
        );
        processedRecord.hasChanges = hasChanges;
        
        return processedRecord;
    });
    
    console.log('Всего записей после обработки:', state.records.length);
    
    // Обновляем статистику
    updateStatistics();
    
    // Сбрасываем виртуализацию - начинаем с меньшего количества для быстрой загрузки
    state.visibleStart = 0;
    state.visibleEnd = Math.min(20, state.records.length); // Начальные 20 записей для быстрой загрузки
    
    console.log('Начинаю рендеринг...');
    // Используем requestAnimationFrame для плавного рендеринга
    requestAnimationFrame(() => {
        renderRecords();
        setupVirtualScroll();
        console.log('renderRecords завершена');
    });
}

function renderRecords() {
    if (!state.records.length) {
        elements.recordsContainer.innerHTML = '<div class="hint">Данные не загружены.</div>';
        return;
    }
    
    const showOnlyChanges = elements.showOnlyChanges?.checked ?? false;
    const showOnlyChangedFields = elements.showOnlyChangedFields?.checked ?? false;
    
    // Фильтруем записи по фильтру изменений
    let visibleRecords = showOnlyChanges 
        ? state.records.filter(r => r.hasChanges)
        : state.records;
    
    // Если включен фильтр "Показать только измененные поля", 
    // дополнительно фильтруем записи, в которых нет измененных полей
    if (showOnlyChangedFields) {
        visibleRecords = visibleRecords.filter(record => {
            return record.fields.some(field => 
                field.status === 'changed' || field.status === 'added' || field.status === 'removed'
            );
        });
    }
    
    // Фильтруем по поисковому запросу
    const searchResultsCount = visibleRecords.length;
    if (state.searchQuery) {
        visibleRecords = visibleRecords.filter(record => matchesSearch(record, state.searchQuery));
    }
    
    if (visibleRecords.length === 0) {
        let hintMessage = 'Нет записей для отображения.';
        let hintClass = 'hint';
        
        if (state.searchQuery) {
            hintMessage = `
                <div style="text-align: center; padding: 20px;">
                    <div style="font-size: 2rem; margin-bottom: 12px;">🔍</div>
                    <div style="font-size: 1.1rem; margin-bottom: 8px; color: var(--text);">Ничего не найдено</div>
                    <div style="color: var(--muted); margin-bottom: 16px;">
                        По запросу: <strong style="color: var(--accent);">"${escapeHtml(state.searchQuery)}"</strong>
                    </div>
                    <div style="color: var(--muted); font-size: 0.9rem;">
                        Попробуйте изменить поисковый запрос или снять фильтр "Показать только изменения"
                    </div>
                </div>
            `;
            hintClass = 'hint search-no-results';
        } else if (showOnlyChanges || showOnlyChangedFields) {
            let filterText = '';
            if (showOnlyChanges && showOnlyChangedFields) {
                filterText = 'галочки "Показать только изменения" и "Показать только измененные поля"';
            } else if (showOnlyChanges) {
                filterText = 'галочку "Показать только изменения"';
            } else {
                filterText = 'галочку "Показать только измененные поля"';
            }
            
            hintMessage = `
                <div style="text-align: center; padding: 20px;">
                    <div style="font-size: 2rem; margin-bottom: 12px;">📋</div>
                    <div style="font-size: 1.1rem; margin-bottom: 8px; color: var(--text);">Нет изменений</div>
                    <div style="color: var(--muted); font-size: 0.9rem;">
                        Попробуйте снять ${filterText}
                    </div>
                </div>
            `;
        }
        
        elements.recordsContainer.innerHTML = `<div class="${hintClass}">${hintMessage}</div>`;
        updateProgressIndicator(0, visibleRecords.length);
        return;
    }
    
    console.log(`Рендеринг записей ${state.visibleStart} - ${state.visibleEnd} из ${visibleRecords.length} (всего ${state.records.length})`);
    
    // Виртуализация: рендерим только видимые записи + небольшой буфер
    const buffer = 5;
    const start = Math.max(0, state.visibleStart - buffer);
    const end = Math.min(visibleRecords.length, state.visibleEnd + buffer);
    
    const fragment = document.createDocumentFragment();
    
    // Показываем информацию о результатах поиска
    if (state.searchQuery && visibleRecords.length > 0) {
        const searchInfo = document.createElement('div');
        searchInfo.className = 'search-results-info';
        searchInfo.innerHTML = `
            <div class="search-info-content">
                <span class="search-icon">🔍</span>
                <span class="search-text">Найдено записей: <strong>${visibleRecords.length}</strong> из ${searchResultsCount}</span>
                <span class="search-query">по запросу: "<strong>${escapeHtml(state.searchQuery)}</strong>"</span>
            </div>
        `;
        fragment.appendChild(searchInfo);
    }
    
    // Создаем спейсер для записей выше видимой области
    if (start > 0) {
        const spacer = document.createElement('div');
        spacer.className = 'virtual-spacer';
        spacer.style.height = `${start * state.itemHeight}px`;
        fragment.appendChild(spacer);
    }

    let renderedCount = 0;
    
    for (let i = start; i < end; i++) {
        const record = visibleRecords[i];
        if (!record) continue;
        
        // Находим оригинальный индекс записи
        const originalIndex = state.records.indexOf(record);
        
        const card = document.createElement('div');
        card.className = 'record' + (record.deleted ? ' deleted' : '');
        card.dataset.recordIndex = originalIndex;

        card.appendChild(buildRecordHeader(record, originalIndex));
        card.appendChild(buildFieldGrid(record, originalIndex));
        fragment.appendChild(card);
        renderedCount++;
    }
    
    // Создаем спейсер для записей ниже видимой области
    if (end < visibleRecords.length) {
        const spacer = document.createElement('div');
        spacer.className = 'virtual-spacer';
        spacer.style.height = `${(visibleRecords.length - end) * state.itemHeight}px`;
        fragment.appendChild(spacer);
    }

    // Очищаем контейнер и добавляем все элементы
    elements.recordsContainer.innerHTML = '';
    elements.recordsContainer.appendChild(fragment);
    
    console.log(`Отрендерено ${renderedCount} записей`);
    
    // Применяем фильтр полей после рендера
    applyFieldFilter();
    
    // Добавляем индикатор прогресса
    updateProgressIndicator(renderedCount, visibleRecords.length);
}

function matchesSearch(record, query) {
    if (!query) return true;
    
    // Поиск по ID
    if (record.id && record.id.toLowerCase().includes(query)) {
        return true;
    }
    
    // Поиск по значениям полей
    if (record.fields) {
        for (const field of record.fields) {
            const oldValue = (field.oldValue || '').toLowerCase();
            const newValue = (field.newValue || '').toLowerCase();
            const mergedValue = (field.mergedValue || '').toLowerCase();
            const key = (field.key || '').toLowerCase();
            
            if (oldValue.includes(query) || 
                newValue.includes(query) || 
                mergedValue.includes(query) ||
                key.includes(query)) {
                return true;
            }
        }
    }
    
    return false;
}

function updateProgressIndicator(renderedCount = 0, totalVisible = 0) {
    const existing = document.getElementById('progressIndicator');
    if (existing) {
        existing.remove();
    }
    
    if (!state.records.length) return;
    
    const showOnlyChanges = elements.showOnlyChanges?.checked ?? false;
    const showOnlyChangedFields = elements.showOnlyChangedFields?.checked ?? false;
    
    let visibleRecords = showOnlyChanges 
        ? state.records.filter(r => r.hasChanges)
        : state.records;
    
    // Если включен фильтр "Показать только измененные поля", 
    // дополнительно фильтруем записи, в которых нет измененных полей
    if (showOnlyChangedFields) {
        visibleRecords = visibleRecords.filter(record => {
            return record.fields.some(field => 
                field.status === 'changed' || field.status === 'added' || field.status === 'removed'
            );
        });
    }
    
    // Учитываем поиск
    if (state.searchQuery) {
        visibleRecords = visibleRecords.filter(record => matchesSearch(record, state.searchQuery));
    }
    
    const total = totalVisible || visibleRecords.length;
    const shown = renderedCount || Math.min(state.visibleEnd, total);
    const percent = total > 0 ? Math.round((shown / total) * 100) : 0;
    
    const indicator = document.createElement('div');
    indicator.id = 'progressIndicator';
    indicator.className = 'progress-indicator';
    let statusText = `Показано: ${shown} из ${total} записей (${percent}%)`;
    if (showOnlyChanges) {
        statusText += ` | Всего записей: ${state.records.length}`;
    }
    if (state.searchQuery) {
        statusText += ` | Поиск: "${state.searchQuery}"`;
    }
    indicator.textContent = statusText;
    
    const wrapper = elements.recordsWrapper || elements.recordsContainer.parentElement;
    if (wrapper) {
        wrapper.insertBefore(indicator, elements.recordsContainer);
    }
}

function setupVirtualScroll() {
    if (!elements.recordsWrapper) {
        console.warn('recordsWrapper не найден, виртуализация отключена');
        return;
    }
    
    let scrollTimeout;
    const container = elements.recordsWrapper;
    
    container.addEventListener('scroll', () => {
        clearTimeout(scrollTimeout);
        scrollTimeout = setTimeout(() => {
            handleScroll();
        }, 50); // Дебаунс для производительности
    }, { passive: true });
    
    // Начальная загрузка
    handleScroll();
}

function handleScroll() {
    const container = elements.recordsWrapper;
    if (!container) return;
    
    const showOnlyChanges = elements.showOnlyChanges?.checked ?? false;
    const showOnlyChangedFields = elements.showOnlyChangedFields?.checked ?? false;
    
    let visibleRecords = showOnlyChanges 
        ? state.records.filter(r => r.hasChanges)
        : state.records;
    
    // Если включен фильтр "Показать только измененные поля", 
    // дополнительно фильтруем записи, в которых нет измененных полей
    if (showOnlyChangedFields) {
        visibleRecords = visibleRecords.filter(record => {
            return record.fields.some(field => 
                field.status === 'changed' || field.status === 'added' || field.status === 'removed'
            );
        });
    }
    
    // Учитываем поиск
    if (state.searchQuery) {
        visibleRecords = visibleRecords.filter(record => matchesSearch(record, state.searchQuery));
    }
    
    if (visibleRecords.length === 0) return;
    
    const scrollTop = container.scrollTop;
    const containerHeight = container.clientHeight;
    
    // Вычисляем какие записи должны быть видимы
    const start = Math.floor(scrollTop / state.itemHeight);
    const visibleCount = Math.ceil(containerHeight / state.itemHeight);
    const end = start + visibleCount + 10; // +10 для буфера
    
    // Обновляем только если изменилось значительно (для производительности)
    if (Math.abs(start - state.visibleStart) > 3 || Math.abs(end - state.visibleEnd) > 3) {
        state.visibleStart = Math.max(0, start);
        state.visibleEnd = Math.min(visibleRecords.length, end);
        renderRecords();
    }
}

function buildRecordHeader(record, recordIndex) {
    const header = document.createElement('div');
    header.className = 'record-header';

    const idSpan = document.createElement('span');
    idSpan.className = 'record-id';
    idSpan.textContent = `ID ${record.id}`;
    header.appendChild(idSpan);

    const actions = document.createElement('div');
    actions.className = 'record-actions';
    actions.appendChild(buildButton('Принять OLD', 'record-old', recordIndex, null, 'tiny'));
    actions.appendChild(buildButton('Принять NEW', 'record-new', recordIndex, null, 'tiny'));
    actions.appendChild(buildButton('Удалить', 'record-delete', recordIndex, null, 'tiny danger'));
    
    header.appendChild(actions);
    return header;
}

function buildFieldGrid(record, recordIndex) {
    const container = document.createElement('div');
    container.className = 'fields-container';

    // Добавляем заголовки колонок в каждую запись
    const headerRow = document.createElement('div');
    headerRow.className = 'field-header-row';
    
    const labelHeader = document.createElement('div');
    labelHeader.className = 'field-header-label';
    labelHeader.textContent = 'Поле';
    headerRow.appendChild(labelHeader);
    
    const oldHeader = document.createElement('div');
    oldHeader.className = 'field-header-old';
    oldHeader.textContent = 'OLD';
    headerRow.appendChild(oldHeader);
    
    const newHeader = document.createElement('div');
    newHeader.className = 'field-header-new';
    newHeader.textContent = 'NEW';
    headerRow.appendChild(newHeader);
    
    const mergedHeader = document.createElement('div');
    mergedHeader.className = 'field-header-merged';
    mergedHeader.textContent = 'MERGED';
    headerRow.appendChild(mergedHeader);
    
    container.appendChild(headerRow);

    const showOnlyChangedFields = elements.showOnlyChangedFields?.checked ?? false;

    record.fields.forEach((field, fieldIndex) => {
        const row = document.createElement('div');
        row.className = 'field-row';
        applyFieldStatusClass(row, field);
        setFieldDataset(row, recordIndex, fieldIndex);

        // Скрываем поля без изменений, если фильтр активен
        if (showOnlyChangedFields && field.status === 'same') {
            row.style.display = 'none';
            row.classList.add('filtered-out');
        } else {
            row.style.display = '';
            row.classList.remove('filtered-out');
        }

        const label = document.createElement('div');
        label.className = 'field-label';
        label.textContent = field.key;
        row.appendChild(label);

        const oldValue = document.createElement('div');
        oldValue.className = 'field-old';
        oldValue.textContent = field.oldValue ?? '—';
        row.appendChild(oldValue);

        const newValue = document.createElement('div');
        newValue.className = 'field-new';
        newValue.textContent = field.newValue ?? '—';
        row.appendChild(newValue);

        const mergedValue = document.createElement('div');
        mergedValue.className = 'field-merged';
        mergedValue.contentEditable = !field.deleted && !record.deleted;
        mergedValue.textContent = field.mergedValue ?? '';
        mergedValue.dataset.recordIndex = recordIndex;
        mergedValue.dataset.fieldIndex = fieldIndex;
        if (field.deleted || record.deleted) {
            mergedValue.style.opacity = '0.5';
        }
        row.appendChild(mergedValue);

        container.appendChild(row);
    });

    return container;
}

function applyFieldStatusClass(element, field) {
    element.classList.remove('same', 'changed', 'added', 'removed');
    if (field.deleted) {
        element.classList.add('removed');
    } else {
        element.classList.add(field.status || 'same');
    }
}

function setFieldDataset(element, recordIndex, fieldIndex) {
    element.dataset.recordIndex = recordIndex;
    element.dataset.fieldIndex = fieldIndex;
}

function applyFieldClass(element, field) {
    element.classList.remove('status-same', 'status-changed', 'status-added', 'status-removed');
    element.classList.add(`status-${field.status || 'same'}`);
    if (field.deleted) {
        element.classList.add('deleted');
    } else {
        element.classList.remove('deleted');
    }
    return element;
}

function buildButton(label, action, recordIndex, fieldIndex = null, size = '') {
    const btn = document.createElement('button');
    btn.className = 'btn' + (size ? ' ' + size : '');
    btn.textContent = label;
    btn.dataset.action = action;
    btn.dataset.recordIndex = recordIndex;
    if (fieldIndex !== null) {
        btn.dataset.fieldIndex = fieldIndex;
    }
    return btn;
}

function handleRecordsClick(event) {
    const button = event.target.closest('button');
    if (!button || !button.dataset.action) {
        return;
    }
    const recordIndex = Number(button.dataset.recordIndex);
    const fieldIndex = button.dataset.fieldIndex !== undefined
        ? Number(button.dataset.fieldIndex)
        : null;
    const record = state.records[recordIndex];
    if (!record) return;

    switch (button.dataset.action) {
        case 'record-old':
            record.fields.forEach(field => {
                field.mergedValue = field.oldValue ?? '';
                field.deleted = false;
            });
            record.deleted = false;
            break;
        case 'record-new':
            record.fields.forEach(field => {
                field.mergedValue = field.newValue ?? '';
                field.deleted = false;
            });
            record.deleted = false;
            break;
        case 'record-reset':
            record.fields.forEach(field => {
                field.mergedValue = field.newValue ?? field.oldValue ?? '';
                field.deleted = false;
            });
            record.deleted = false;
            break;
        case 'record-delete':
            record.deleted = !record.deleted;
            record.fields.forEach(field => field.deleted = record.deleted);
            break;
        case 'field-old':
            setFieldValue(record, fieldIndex, record.fields[fieldIndex]?.oldValue ?? '');
            break;
        case 'field-new':
            setFieldValue(record, fieldIndex, record.fields[fieldIndex]?.newValue ?? '');
            break;
        case 'field-reset':
            setFieldValue(record, fieldIndex,
                record.fields[fieldIndex]?.newValue ?? record.fields[fieldIndex]?.oldValue ?? '');
            break;
        case 'field-delete':
            toggleFieldDelete(record, fieldIndex);
            break;
        default:
            break;
    }
    updateStatuses(record);
    renderRecords();
    // Применяем фильтр полей после рендера
    applyFieldFilter();
}

function setFieldValue(record, fieldIndex, value) {
    const field = record.fields[fieldIndex];
    if (!field) return;
    field.mergedValue = value || '';
    field.deleted = false;
}

function toggleFieldDelete(record, fieldIndex) {
    const field = record.fields[fieldIndex];
    if (!field) return;
    field.deleted = !field.deleted;
    if (!field.deleted) {
        field.mergedValue = field.newValue ?? field.oldValue ?? '';
    }
}

function handleMergedInput(event) {
    const target = event.target;
    if (!target.classList.contains('field-merged')) {
        return;
    }
    const recordIndex = Number(target.dataset.recordIndex);
    const fieldIndex = Number(target.dataset.fieldIndex);
    const field = state.records[recordIndex]?.fields[fieldIndex];
    if (!field) return;

    field.mergedValue = target.textContent || target.innerText || '';
    field.deleted = false;
    updateStatuses(state.records[recordIndex]);
    refreshFieldStyles(recordIndex);
    // Применяем фильтр полей после обновления статусов
    applyFieldFilter();
}

function refreshFieldStyles(recordIndex) {
    const record = state.records[recordIndex];
    if (!record) return;
    const showOnlyChangedFields = elements.showOnlyChangedFields?.checked ?? false;
    record.fields.forEach((field, fieldIndex) => {
        const row = document.querySelector(`.field-row[data-record-index="${recordIndex}"][data-field-index="${fieldIndex}"]`);
        if (row) {
            applyFieldStatusClass(row, field);
            // Применяем фильтр полей
            if (showOnlyChangedFields && field.status === 'same') {
                row.style.display = 'none';
                row.classList.add('filtered-out');
            } else {
                row.style.display = '';
                row.classList.remove('filtered-out');
            }
            const mergedCell = row.querySelector('.field-merged');
            if (mergedCell && mergedCell.textContent !== field.mergedValue) {
                mergedCell.textContent = field.mergedValue ?? '';
            }
        }
    });
}

function applyFieldFilter() {
    const showOnlyChangedFields = elements.showOnlyChangedFields?.checked ?? false;
    const allFieldRows = document.querySelectorAll('.field-row');
    
    allFieldRows.forEach(row => {
        const isSame = row.classList.contains('same');
        if (showOnlyChangedFields && isSame) {
            row.style.display = 'none';
            row.classList.add('filtered-out');
        } else {
            row.style.display = '';
            row.classList.remove('filtered-out');
        }
    });
}

function updateStatuses(record) {
    record.fields.forEach(field => {
        if (field.deleted || record.deleted) {
            field.status = 'removed';
            return;
        }
        if (!field.newValue && field.oldValue) {
            field.status = 'removed';
            return;
        }
        if (field.newValue && !field.oldValue) {
            field.status = 'added';
            return;
        }
        if (field.oldValue !== field.newValue) {
            field.status = 'changed';
            return;
        }
        if ((field.mergedValue ?? '') !== (field.newValue ?? '')) {
            field.status = 'changed';
            return;
        }
        field.status = 'same';
    });
}

function showMassActions() {
    if (!state.records.length) {
        setStatus('Нет данных для анализа', true);
        return;
    }
    
    // Группируем похожие изменения
    const changeGroups = groupSimilarChanges();
    
    if (changeGroups.length === 0) {
        setStatus('Нет похожих изменений для группировки', true);
        return;
    }
    
    // Показываем панель
    elements.massActionsPanel.style.display = 'block';
    elements.massActionsList.innerHTML = '';
    
    // Сортируем по количеству вхождений (самые частые сверху)
    changeGroups.sort((a, b) => b.count - a.count);
    
    changeGroups.forEach((group, index) => {
        const item = document.createElement('div');
        item.className = 'mass-action-item';
        
        const preview = document.createElement('div');
        preview.className = 'mass-action-preview';
        
        const fieldName = document.createElement('strong');
        fieldName.textContent = group.fieldKey;
        preview.appendChild(fieldName);
        
        const changeInfo = document.createElement('div');
        changeInfo.className = 'mass-action-change';
        changeInfo.innerHTML = `
            <span class="old-preview">${escapeHtml(group.oldValue || '—').substring(0, 50)}${group.oldValue && group.oldValue.length > 50 ? '...' : ''}</span>
            <span>→</span>
            <span class="new-preview">${escapeHtml(group.newValue || '—').substring(0, 50)}${group.newValue && group.newValue.length > 50 ? '...' : ''}</span>
        `;
        preview.appendChild(changeInfo);
        
        const count = document.createElement('div');
        count.className = 'mass-action-count';
        count.textContent = `Найдено: ${group.count} записей`;
        preview.appendChild(count);
        
        const actions = document.createElement('div');
        actions.className = 'mass-action-buttons';
        
        const applyNewBtn = document.createElement('button');
        applyNewBtn.className = 'btn tiny';
        applyNewBtn.textContent = `Принять NEW (${group.count})`;
        applyNewBtn.onclick = () => applyMassChange(group, 'new');
        actions.appendChild(applyNewBtn);
        
        const applyOldBtn = document.createElement('button');
        applyOldBtn.className = 'btn tiny';
        applyOldBtn.textContent = `Принять OLD (${group.count})`;
        applyOldBtn.onclick = () => applyMassChange(group, 'old');
        actions.appendChild(applyOldBtn);
        
        item.appendChild(preview);
        item.appendChild(actions);
        elements.massActionsList.appendChild(item);
    });
}

function groupSimilarChanges() {
    const groups = new Map();
    
    state.records.forEach((record, recordIndex) => {
        record.fields.forEach((field, fieldIndex) => {
            // Группируем только измененные поля
            if (field.status === 'changed' && field.oldValue && field.newValue) {
                const key = `${field.key}|||${field.oldValue}|||${field.newValue}`;
                
                if (!groups.has(key)) {
                    groups.set(key, {
                        fieldKey: field.key,
                        oldValue: field.oldValue,
                        newValue: field.newValue,
                        count: 0,
                        records: []
                    });
                }
                
                const group = groups.get(key);
                group.count++;
                group.records.push({ recordIndex, fieldIndex });
            }
        });
    });
    
    // Фильтруем группы с более чем одним вхождением
    return Array.from(groups.values()).filter(g => g.count > 1);
}

function applyMassChange(group, action) {
    let applied = 0;
    
    group.records.forEach(({ recordIndex, fieldIndex }) => {
        const record = state.records[recordIndex];
        if (!record || !record.fields[fieldIndex]) return;
        
        const field = record.fields[fieldIndex];
        
        // Проверяем, что изменение все еще актуально
        if (field.oldValue === group.oldValue && field.newValue === group.newValue) {
            if (action === 'new') {
                field.mergedValue = field.newValue;
            } else if (action === 'old') {
                field.mergedValue = field.oldValue;
            }
            field.deleted = false;
            updateStatuses(record);
            applied++;
        }
    });
    
    setStatus(`Применено к ${applied} из ${group.count} записей`);
    renderRecords();
    
    // Обновляем панель массовых действий
    showMassActions();
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function updateStatistics() {
    if (!elements.statsContent || !state.records.length) {
        return;
    }
    
    let stats = {
        total: state.records.length,
        added: 0,      // Только в NEW
        removed: 0,   // Только в OLD
        common: 0,     // Есть в обоих
        withChanges: 0, // С изменениями полей
        withoutChanges: 0, // Без изменений
        totalChangedFields: 0,
        totalAddedFields: 0,
        totalRemovedFields: 0
    };
    
    state.records.forEach(record => {
        const hasOld = record.fields.some(f => f.oldValue !== null);
        const hasNew = record.fields.some(f => f.newValue !== null);
        
        if (!hasOld && hasNew) {
            stats.added++;
        } else if (hasOld && !hasNew) {
            stats.removed++;
        } else if (hasOld && hasNew) {
            stats.common++;
            
            if (record.hasChanges) {
                stats.withChanges++;
            } else {
                stats.withoutChanges++;
            }
        }
        
        record.fields.forEach(field => {
            if (field.status === 'changed') {
                stats.totalChangedFields++;
            } else if (field.status === 'added') {
                stats.totalAddedFields++;
            } else if (field.status === 'removed') {
                stats.totalRemovedFields++;
            }
        });
    });
    
    const html = `
        <div class="stats-section">
            <div class="stats-item">
                <span class="stats-label">Всего записей:</span>
                <span class="stats-value">${stats.total}</span>
            </div>
            <div class="stats-item">
                <span class="stats-label">Новых (только в NEW):</span>
                <span class="stats-value added">${stats.added}</span>
            </div>
            <div class="stats-item">
                <span class="stats-label">Удаленных (только в OLD):</span>
                <span class="stats-value removed">${stats.removed}</span>
            </div>
            <div class="stats-item">
                <span class="stats-label">Совпадающих ID:</span>
                <span class="stats-value">${stats.common}</span>
            </div>
        </div>
        <div class="stats-section">
            <div class="stats-item">
                <span class="stats-label">С изменениями:</span>
                <span class="stats-value changed">${stats.withChanges}</span>
            </div>
            <div class="stats-item">
                <span class="stats-label">Без изменений:</span>
                <span class="stats-value">${stats.withoutChanges}</span>
            </div>
        </div>
        <div class="stats-section">
            <div class="stats-item">
                <span class="stats-label">Изменено полей:</span>
                <span class="stats-value changed">${stats.totalChangedFields}</span>
            </div>
            <div class="stats-item">
                <span class="stats-label">Добавлено полей:</span>
                <span class="stats-value added">${stats.totalAddedFields}</span>
            </div>
            <div class="stats-item">
                <span class="stats-label">Удалено полей:</span>
                <span class="stats-value removed">${stats.totalRemovedFields}</span>
            </div>
        </div>
    `;
    
    elements.statsContent.innerHTML = html;
}

async function handleExport() {
    if (!state.records.length) {
        setStatus('Нет данных для экспорта', true);
        return;
    }
    try {
        setStatus('Формирую файл...');
        const response = await fetch('/export', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                format: state.format,
                records: state.records
            })
        });
        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || 'Ошибка экспорта');
        }
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = state.format === 'block' ? 'merged_item_name.txt' : 'merged.txt';
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
        setStatus('Экспорт завершён');
    } catch (error) {
        console.error(error);
        setStatus(error.message, true);
    }
}

