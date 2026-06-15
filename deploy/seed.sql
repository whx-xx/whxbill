USE whx_bill;

INSERT INTO sys_role (id, role_code, role_name, description, status) VALUES
(1, 'ROLE_ADMIN', '系统管理员', '拥有后台全部权限', 1),
(2, 'ROLE_USER', '普通用户', '拥有个人账本权限', 1)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), description = VALUES(description), status = VALUES(status);

INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, permission_type, path, component, icon, sort_order, status) VALUES
(1, 0, 'admin:user:list', '用户列表', 'BUTTON', '/users', '', '', 1, 1),
(2, 0, 'admin:user:export', '用户导出', 'BUTTON', '/users/export', '', '', 2, 1),
(3, 0, 'admin:dict:list', '字典列表', 'BUTTON', '/dicts', '', '', 3, 1),
(4, 0, 'admin:notice:list', '公告列表', 'BUTTON', '/notices', '', '', 4, 1),
(5, 0, 'admin:log:list', '日志列表', 'BUTTON', '/logs', '', '', 5, 1),
(6, 0, 'admin:role:list', '角色列表', 'BUTTON', '/roles', '', '', 6, 1),
(7, 0, 'admin:permission:list', '权限列表', 'BUTTON', '/permissions', '', '', 7, 1),
(8, 0, 'bill:list', '账单列表', 'BUTTON', '/bills', '', '', 8, 1),
(9, 0, 'bill:create', '保存账单', 'BUTTON', '/bills/save', '', '', 9, 1),
(10, 0, 'admin:user:create', '用户新增', 'BUTTON', '/users', '', '', 10, 1),
(11, 0, 'admin:user:update', '用户编辑', 'BUTTON', '/users', '', '', 11, 1),
(12, 0, 'admin:user:delete', '用户删除', 'BUTTON', '/users', '', '', 12, 1),
(13, 0, 'admin:role:create', '角色新增', 'BUTTON', '/roles', '', '', 13, 1),
(14, 0, 'admin:role:update', '角色编辑', 'BUTTON', '/roles', '', '', 14, 1),
(15, 0, 'admin:role:delete', '角色删除', 'BUTTON', '/roles', '', '', 15, 1),
(16, 0, 'admin:notice:create', '公告新增', 'BUTTON', '/notices', '', '', 16, 1),
(17, 0, 'admin:notice:update', '公告编辑', 'BUTTON', '/notices', '', '', 17, 1),
(18, 0, 'admin:notice:delete', '公告删除', 'BUTTON', '/notices', '', '', 18, 1),
(19, 0, 'admin:dict:create', '字典新增', 'BUTTON', '/dicts', '', '', 19, 1),
(20, 0, 'admin:dict:update', '字典编辑', 'BUTTON', '/dicts', '', '', 20, 1),
(21, 0, 'admin:dict:delete', '字典删除', 'BUTTON', '/dicts', '', '', 21, 1)
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), sort_order = VALUES(sort_order), status = VALUES(status);

INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8), (1, 9),
(1, 10), (1, 11), (1, 12), (1, 13), (1, 14), (1, 15), (1, 16), (1, 17), (1, 18), (1, 19), (1, 20), (1, 21),
(2, 8), (2, 9)
ON DUPLICATE KEY UPDATE permission_id = VALUES(permission_id);

INSERT INTO sys_user (id, username, password, nickname, email, status, user_type) VALUES
(1, 'admin', '{noop}Admin@123456', '管理员', 'admin@whxbill.com', 1, 0),
(2, 'demo', '{noop}Demo@123456', '演示用户', 'demo@whxbill.com', 1, 1)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), email = VALUES(email), status = VALUES(status), user_type = VALUES(user_type);

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1), (2, 2)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

DELETE FROM biz_attachment WHERE user_id = 2;
DELETE FROM biz_bill WHERE user_id = 2;
DELETE FROM biz_budget WHERE user_id = 2;
DELETE FROM biz_category WHERE user_id = 2;
DELETE FROM biz_account WHERE user_id = 2;
DELETE FROM biz_book WHERE user_id = 2;
DELETE FROM sys_message WHERE user_id = 2;

INSERT INTO biz_book (id, user_id, book_name, currency_code, is_default) VALUES
(1, 2, '个人生活账本', 'CNY', 1),
(2, 2, '家庭共用账本', 'CNY', 0)
ON DUPLICATE KEY UPDATE book_name = VALUES(book_name), currency_code = VALUES(currency_code), is_default = VALUES(is_default);

INSERT INTO biz_account (id, user_id, book_id, account_name, account_type, balance, color_tag, sort_order) VALUES
(1, 2, 1, '随身现金', 'CASH', 860.00, '#26A69A', 0),
(2, 2, 1, '招商银行储蓄卡', 'BANK', 12840.50, '#4F8EF7', 1),
(3, 2, 1, '支付宝余额', 'ALIPAY', 2350.80, '#1677FF', 2),
(4, 2, 1, '微信钱包', 'WECHAT', 620.45, '#43C66A', 3),
(5, 2, 2, '家庭结算卡', 'BANK', 18600.00, '#5B8FF9', 0)
ON DUPLICATE KEY UPDATE
account_name = VALUES(account_name),
account_type = VALUES(account_type),
balance = VALUES(balance),
color_tag = VALUES(color_tag),
sort_order = VALUES(sort_order);

INSERT INTO biz_category (id, user_id, book_id, parent_id, category_name, category_type, icon, level, sort_order) VALUES
(1, 2, 1, 0, '食品餐饮', 'EXPENSE', 'food', 1, 0),
(2, 2, 1, 0, '出行交通', 'EXPENSE', 'bus', 1, 1),
(3, 2, 1, 0, '居家生活', 'EXPENSE', 'home', 1, 2),
(4, 2, 1, 0, '购物消费', 'EXPENSE', 'shopping-bag', 1, 3),
(5, 2, 1, 0, '休闲娱乐', 'EXPENSE', 'gamepad-2', 1, 4),
(6, 2, 1, 0, '工资', 'INCOME', 'salary', 1, 3),
(7, 2, 1, 0, '奖金', 'INCOME', 'bonus', 1, 5),
(8, 2, 1, 0, '其他', 'INCOME', 'other', 1, 10),
(9, 2, 2, 0, '家庭采购', 'EXPENSE', 'shopping-cart', 1, 0),
(10, 2, 2, 0, '房屋支出', 'EXPENSE', 'building-2', 1, 1),
(11, 2, 1, 0, '礼金人情', 'INCOME', 'gift', 1, 7),
(12, 2, 1, 0, '健康医疗', 'EXPENSE', 'health', 1, 5),
(13, 2, 1, 0, '送礼人情', 'EXPENSE', 'gift', 1, 6),
(14, 2, 1, 0, '文化教育', 'EXPENSE', 'culture', 1, 7),
(15, 2, 1, 0, '其他', 'EXPENSE', 'other', 1, 8),
(16, 2, 1, 0, '报销', 'INCOME', 'reimburse', 1, 0),
(17, 2, 1, 0, '补贴', 'INCOME', 'subsidy', 1, 1),
(18, 2, 1, 0, '二手闲置', 'INCOME', 'second-hand', 1, 2),
(19, 2, 1, 0, '兼职外快', 'INCOME', 'money', 1, 4),
(20, 2, 1, 0, '借入', 'INCOME', 'borrow', 1, 6),
(21, 2, 1, 0, '理财盈利', 'INCOME', 'profit', 1, 8),
(22, 2, 1, 0, '中奖', 'INCOME', 'lottery', 1, 9),
(101, 2, 1, 1, '其他', 'EXPENSE', 'food', 2, 0),
(102, 2, 1, 1, '粮油调味', 'EXPENSE', 'food', 2, 1),
(103, 2, 1, 1, '请客吃饭', 'EXPENSE', 'food', 2, 2),
(104, 2, 1, 1, '生鲜食品', 'EXPENSE', 'food', 2, 3),
(105, 2, 1, 1, '休闲零食', 'EXPENSE', 'food', 2, 4),
(106, 2, 1, 1, '饮料酒水', 'EXPENSE', 'coffee', 2, 5),
(107, 2, 1, 1, '晚餐', 'EXPENSE', 'dish', 2, 6),
(108, 2, 1, 1, '午餐', 'EXPENSE', 'bowl', 2, 7),
(109, 2, 1, 1, '早餐', 'EXPENSE', 'coffee', 2, 8),
(201, 2, 1, 2, '其他', 'EXPENSE', 'bus', 2, 0),
(202, 2, 1, 2, '保养修车', 'EXPENSE', 'tools', 2, 1),
(203, 2, 1, 2, '飞机', 'EXPENSE', 'promotion', 2, 2),
(204, 2, 1, 2, '火车', 'EXPENSE', 'train', 2, 3),
(205, 2, 1, 2, '加油', 'EXPENSE', 'gas', 2, 4),
(206, 2, 1, 2, '停车费', 'EXPENSE', 'parking', 2, 5),
(207, 2, 1, 2, '公共交通', 'EXPENSE', 'bus', 2, 6),
(208, 2, 1, 2, '打车', 'EXPENSE', 'car', 2, 7),
(301, 2, 1, 3, '其他', 'EXPENSE', 'home', 2, 0),
(302, 2, 1, 3, '家政清洁', 'EXPENSE', 'brush', 2, 1),
(303, 2, 1, 3, '车位费', 'EXPENSE', 'parking', 2, 2),
(304, 2, 1, 3, '房租还贷', 'EXPENSE', 'house', 2, 3),
(305, 2, 1, 3, '物业费', 'EXPENSE', 'home', 2, 4),
(306, 2, 1, 3, '燃气费', 'EXPENSE', 'gas', 2, 5),
(307, 2, 1, 3, '水费', 'EXPENSE', 'water', 2, 6),
(308, 2, 1, 3, '电费', 'EXPENSE', 'lightning', 2, 7),
(309, 2, 1, 3, '话费宽带', 'EXPENSE', 'phone', 2, 8),
(401, 2, 1, 4, '其他', 'EXPENSE', 'shopping-bag', 2, 0),
(402, 2, 1, 4, '装修装饰', 'EXPENSE', 'brush', 2, 1),
(403, 2, 1, 4, '办公用品', 'EXPENSE', 'printer', 2, 2),
(404, 2, 1, 4, '宠物用品', 'EXPENSE', 'pet', 2, 3),
(405, 2, 1, 4, '服饰运动', 'EXPENSE', 'goods', 2, 4),
(406, 2, 1, 4, '母婴玩具', 'EXPENSE', 'present', 2, 5),
(407, 2, 1, 4, '配饰腕表', 'EXPENSE', 'watch', 2, 6),
(408, 2, 1, 4, '生活电器', 'EXPENSE', 'refrigerator', 2, 7),
(409, 2, 1, 4, '虚拟充值', 'EXPENSE', 'cellphone', 2, 8),
(410, 2, 1, 4, '手机数码', 'EXPENSE', 'iphone', 2, 9),
(411, 2, 1, 4, '个护美妆', 'EXPENSE', 'brush', 2, 10),
(412, 2, 1, 4, '日常家居', 'EXPENSE', 'house', 2, 11),
(501, 2, 1, 5, '其他', 'EXPENSE', 'gamepad-2', 2, 0),
(502, 2, 1, 5, '演出', 'EXPENSE', 'ticket', 2, 1),
(503, 2, 1, 5, '酒吧', 'EXPENSE', 'goblet', 2, 2),
(504, 2, 1, 5, '棋牌桌游', 'EXPENSE', 'tickets', 2, 3),
(505, 2, 1, 5, '足浴按摩', 'EXPENSE', 'service', 2, 4),
(506, 2, 1, 5, '运动健身', 'EXPENSE', 'basketball', 2, 5),
(507, 2, 1, 5, '电影唱歌', 'EXPENSE', 'film', 2, 6),
(508, 2, 1, 5, '旅游度假', 'EXPENSE', 'suitcase', 2, 7),
(1201, 2, 1, 12, '其他', 'EXPENSE', 'health', 2, 0),
(1202, 2, 1, 12, '买药', 'EXPENSE', 'first-aid-kit', 2, 1),
(1203, 2, 1, 12, '医院', 'EXPENSE', 'hospital', 2, 2),
(1204, 2, 1, 12, '滋补保健', 'EXPENSE', 'health', 2, 3),
(1301, 2, 1, 13, '其他', 'EXPENSE', 'gift', 2, 0),
(1302, 2, 1, 13, '打赏', 'EXPENSE', 'coin', 2, 1),
(1303, 2, 1, 13, '红包', 'EXPENSE', 'red-packet', 2, 2),
(1304, 2, 1, 13, '借出', 'EXPENSE', 'money', 2, 3),
(1305, 2, 1, 13, '礼物', 'EXPENSE', 'present', 2, 4),
(1306, 2, 1, 13, '孝敬长辈', 'EXPENSE', 'user', 2, 5),
(1401, 2, 1, 14, '其他', 'EXPENSE', 'culture', 2, 0),
(1402, 2, 1, 14, '培训考试', 'EXPENSE', 'edit', 2, 1),
(1403, 2, 1, 14, '书报杂志', 'EXPENSE', 'notebook', 2, 2),
(1404, 2, 1, 14, '学费', 'EXPENSE', 'school', 2, 3),
(1501, 2, 1, 15, '其他', 'EXPENSE', 'other', 2, 0),
(1502, 2, 1, 15, '慈善捐助', 'EXPENSE', 'help', 2, 1),
(1503, 2, 1, 15, '理财支出', 'EXPENSE', 'coin', 2, 2),
(1504, 2, 1, 15, '罚款赔偿', 'EXPENSE', 'document', 2, 3)
ON DUPLICATE KEY UPDATE
parent_id = VALUES(parent_id),
category_name = VALUES(category_name),
category_type = VALUES(category_type),
icon = VALUES(icon),
level = VALUES(level),
sort_order = VALUES(sort_order);

UPDATE biz_category SET book_id = 0 WHERE user_id = 2;

INSERT INTO biz_budget (id, user_id, book_id, category_id, budget_month, budget_amount, used_amount) VALUES
(1, 2, 1, NULL, '2026-06', 8500.00, 5658.00),
(2, 2, 1, 1, '2026-06', 2200.00, 930.70),
(3, 2, 1, 2, '2026-06', 900.00, 350.00),
(4, 2, 1, 3, '2026-06', 2100.00, 373.40),
(5, 2, 2, NULL, '2026-06', 12000.00, 6655.00),
(6, 2, 2, 9, '2026-06', 4200.00, 3115.00),
(7, 2, 1, 4, '2026-06', 2200.00, 2203.90),
(8, 2, 1, 5, '2026-06', 1000.00, 534.00),
(9, 2, 1, 12, '2026-06', 800.00, 286.00),
(10, 2, 1, 14, '2026-06', 1500.00, 680.00),
(11, 2, 1, NULL, '2026-05', 7800.00, 3715.40),
(12, 2, 1, NULL, '2026-07', 8800.00, 2466.10),
(13, 2, 2, NULL, '2026-05', 11000.00, 4380.00),
(14, 2, 2, NULL, '2026-07', 12500.00, 3976.00)
ON DUPLICATE KEY UPDATE
category_id = VALUES(category_id),
budget_month = VALUES(budget_month),
budget_amount = VALUES(budget_amount),
used_amount = VALUES(used_amount);

INSERT INTO biz_bill (id, user_id, book_id, account_id, category_id, bill_type, amount, bill_date, merchant_name, remark, source_type) VALUES
(1, 2, 1, 1, 109, 'EXPENSE', 18.00, '2026-06-01', '社区早餐店', '工作日前早餐', 'MANUAL'),
(2, 2, 1, 2, 207, 'EXPENSE', 12.00, '2026-06-02', '地铁出行', '上班通勤', 'MANUAL'),
(3, 2, 1, 3, 412, 'EXPENSE', 89.90, '2026-06-03', '便利蜂', '补充日用品', 'MANUAL'),
(4, 2, 1, 2, 6, 'INCOME', 9800.00, '2026-06-05', '星云科技', '6 月工资到账', 'MANUAL'),
(5, 2, 1, 4, 507, 'EXPENSE', 128.00, '2026-06-06', '幸福影城', '周末观影', 'MANUAL'),
(6, 2, 1, 2, 412, 'EXPENSE', 699.00, '2026-06-07', '京东到家', '补充居家用品', 'OCR'),
(7, 2, 1, 3, 107, 'EXPENSE', 56.50, '2026-06-08', '禾府捞面', '晚餐外卖', 'MANUAL'),
(8, 2, 1, 2, 7, 'INCOME', 1500.00, '2026-06-10', '季度绩效', '部门绩效奖金', 'MANUAL'),
(9, 2, 2, 5, 9, 'EXPENSE', 980.00, '2026-06-04', '山姆会员商店', '家庭周采购', 'MANUAL'),
(10, 2, 2, 5, 10, 'EXPENSE', 2700.00, '2026-06-09', '万科物业', '季度物业与维修', 'MANUAL'),
(11, 2, 2, 5, 11, 'INCOME', 5600.00, '2026-06-11', '家庭结余转入', '共同账户补充', 'MANUAL'),
(12, 2, 1, 3, 108, 'EXPENSE', 36.80, '2026-06-04', '兰州牛肉面', '工作午餐', 'MANUAL'),
(13, 2, 1, 4, 106, 'EXPENSE', 22.00, '2026-06-04', '瑞幸咖啡', '下午咖啡', 'MANUAL'),
(14, 2, 1, 2, 205, 'EXPENSE', 260.00, '2026-06-05', '中石化加油站', '周末出行加油', 'OCR'),
(15, 2, 1, 1, 208, 'EXPENSE', 42.00, '2026-06-05', '滴滴出行', '晚高峰打车', 'MANUAL'),
(16, 2, 1, 3, 104, 'EXPENSE', 148.60, '2026-06-06', '盒马鲜生', '一周生鲜采购', 'MANUAL'),
(17, 2, 1, 4, 405, 'EXPENSE', 329.00, '2026-06-08', '优衣库', '夏季衬衫', 'MANUAL'),
(18, 2, 1, 2, 308, 'EXPENSE', 186.40, '2026-06-09', '国家电网', '6 月电费', 'MANUAL'),
(19, 2, 1, 2, 307, 'EXPENSE', 58.00, '2026-06-09', '自来水公司', '6 月水费', 'MANUAL'),
(20, 2, 1, 3, 309, 'EXPENSE', 129.00, '2026-06-10', '中国移动', '手机与宽带套餐', 'MANUAL'),
(21, 2, 1, 4, 1202, 'EXPENSE', 86.00, '2026-06-11', '海王星辰药房', '感冒药与维生素', 'MANUAL'),
(22, 2, 1, 2, 1402, 'EXPENSE', 680.00, '2026-06-12', '得到课程', '数据分析课程', 'MANUAL'),
(23, 2, 1, 3, 103, 'EXPENSE', 428.00, '2026-06-13', '湘菜馆', '朋友聚餐', 'MANUAL'),
(24, 2, 1, 4, 506, 'EXPENSE', 198.00, '2026-06-14', '乐刻健身', '月度健身卡', 'MANUAL'),
(25, 2, 1, 2, 410, 'EXPENSE', 399.00, '2026-06-15', '苹果官方旗舰店', '耳机配件', 'MANUAL'),
(26, 2, 1, 3, 1303, 'EXPENSE', 200.00, '2026-06-16', '微信红包', '生日红包', 'MANUAL'),
(27, 2, 1, 1, 109, 'EXPENSE', 16.00, '2026-06-17', '包子铺', '早餐', 'MANUAL'),
(28, 2, 1, 3, 108, 'EXPENSE', 32.50, '2026-06-17', '全家便当', '午餐', 'MANUAL'),
(29, 2, 1, 4, 105, 'EXPENSE', 75.80, '2026-06-18', '零食很忙', '办公室零食', 'MANUAL'),
(30, 2, 1, 2, 6, 'INCOME', 9800.00, '2026-06-20', '星云科技', '项目补贴与报销', 'MANUAL'),
(31, 2, 1, 2, 8, 'INCOME', 320.00, '2026-06-21', '二手平台', '转卖闲置键盘', 'MANUAL'),
(32, 2, 1, 3, 1502, 'EXPENSE', 100.00, '2026-06-22', '公益基金会', '月度公益捐助', 'MANUAL'),
(33, 2, 1, 4, 502, 'EXPENSE', 120.00, '2026-06-23', 'Livehouse', '周末演出票', 'MANUAL'),
(34, 2, 1, 2, 206, 'EXPENSE', 36.00, '2026-06-24', '商场停车场', '停车费', 'MANUAL'),
(35, 2, 1, 3, 408, 'EXPENSE', 498.00, '2026-06-25', '小米商城', '空气净化器滤芯', 'OCR'),
(36, 2, 1, 4, 411, 'EXPENSE', 189.00, '2026-06-26', '屈臣氏', '个护用品', 'MANUAL'),
(37, 2, 1, 2, 1203, 'EXPENSE', 200.00, '2026-06-27', '社区医院', '门诊挂号检查', 'MANUAL'),
(38, 2, 1, 3, 504, 'EXPENSE', 88.00, '2026-06-28', '桌游店', '周末桌游', 'MANUAL'),
(39, 2, 1, 4, 102, 'EXPENSE', 96.50, '2026-06-29', '永辉超市', '粮油调味', 'MANUAL'),
(40, 2, 1, 2, 7, 'INCOME', 1800.00, '2026-06-30', '季度绩效', '项目奖金', 'MANUAL'),
(41, 2, 2, 5, 9, 'EXPENSE', 1320.00, '2026-06-13', '盒马鲜生', '家庭食材采购', 'MANUAL'),
(42, 2, 2, 5, 9, 'EXPENSE', 815.00, '2026-06-18', '山姆会员商店', '家庭日用品', 'MANUAL'),
(43, 2, 2, 5, 10, 'EXPENSE', 520.00, '2026-06-20', '家电维修', '空调清洗维修', 'MANUAL'),
(44, 2, 2, 5, 10, 'EXPENSE', 320.00, '2026-06-25', '物业服务中心', '车位管理费', 'MANUAL'),
(45, 2, 2, 5, 11, 'INCOME', 6200.00, '2026-06-28', '家庭备用金', '共同账户转入', 'MANUAL'),
(46, 2, 1, 3, 107, 'EXPENSE', 52.00, '2026-05-02', '黄焖鸡米饭', '晚餐', 'MANUAL'),
(47, 2, 1, 2, 207, 'EXPENSE', 118.00, '2026-05-03', '公交地铁', '月初通勤充值', 'MANUAL'),
(48, 2, 1, 4, 405, 'EXPENSE', 286.00, '2026-05-06', '迪卡侬', '运动短袖', 'MANUAL'),
(49, 2, 1, 2, 6, 'INCOME', 9600.00, '2026-05-08', '星云科技', '5 月工资到账', 'MANUAL'),
(50, 2, 1, 3, 412, 'EXPENSE', 536.00, '2026-05-10', '京东', '家居清洁用品', 'OCR'),
(51, 2, 1, 2, 305, 'EXPENSE', 620.00, '2026-05-12', '物业服务中心', '物业费', 'MANUAL'),
(52, 2, 1, 4, 507, 'EXPENSE', 116.00, '2026-05-16', '万达影城', '电影票', 'MANUAL'),
(53, 2, 1, 3, 1204, 'EXPENSE', 248.00, '2026-05-18', '同仁堂', '滋补保健', 'MANUAL'),
(54, 2, 1, 4, 1403, 'EXPENSE', 156.00, '2026-05-20', '京东图书', '技术书籍', 'MANUAL'),
(55, 2, 1, 2, 7, 'INCOME', 1200.00, '2026-05-25', '季度绩效', '小组激励', 'MANUAL'),
(56, 2, 1, 3, 508, 'EXPENSE', 1280.00, '2026-05-28', '携程旅行', '周边游酒店', 'MANUAL'),
(57, 2, 1, 4, 1503, 'EXPENSE', 303.40, '2026-05-30', '基金定投', '指数基金定投', 'MANUAL'),
(58, 2, 2, 5, 9, 'EXPENSE', 1680.00, '2026-05-05', '山姆会员商店', '家庭月度采购', 'MANUAL'),
(59, 2, 2, 5, 10, 'EXPENSE', 2700.00, '2026-05-09', '万科物业', '物业与维修', 'MANUAL'),
(60, 2, 2, 5, 11, 'INCOME', 5800.00, '2026-05-15', '家庭结余转入', '共同账户补充', 'MANUAL'),
(61, 2, 1, 1, 109, 'EXPENSE', 19.00, '2026-07-01', '社区早餐店', '早餐', 'MANUAL'),
(62, 2, 1, 3, 108, 'EXPENSE', 42.00, '2026-07-01', '和府捞面', '工作午餐', 'MANUAL'),
(63, 2, 1, 2, 6, 'INCOME', 10000.00, '2026-07-05', '星云科技', '7 月工资到账', 'MANUAL'),
(64, 2, 1, 4, 409, 'EXPENSE', 128.00, '2026-07-06', '腾讯充值', '会员续费', 'MANUAL'),
(65, 2, 1, 2, 304, 'EXPENSE', 1800.00, '2026-07-08', '房东转账', '7 月房租', 'MANUAL'),
(66, 2, 1, 3, 205, 'EXPENSE', 280.00, '2026-07-09', '中石化加油站', '加油', 'OCR'),
(67, 2, 1, 4, 1202, 'EXPENSE', 48.00, '2026-07-10', '药房', '常备药', 'MANUAL'),
(68, 2, 1, 3, 107, 'EXPENSE', 58.60, '2026-07-12', '川菜馆', '晚餐', 'MANUAL'),
(69, 2, 1, 4, 7, 'INCOME', 900.00, '2026-07-15', '项目激励', '阶段奖励', 'MANUAL'),
(70, 2, 1, 3, 412, 'EXPENSE', 90.50, '2026-07-16', '便利蜂', '居家补货', 'MANUAL'),
(71, 2, 2, 5, 9, 'EXPENSE', 1456.00, '2026-07-06', '山姆会员商店', '家庭采购', 'MANUAL'),
(72, 2, 2, 5, 10, 'EXPENSE', 2520.00, '2026-07-10', '万科物业', '季度物业', 'MANUAL')
ON DUPLICATE KEY UPDATE
account_id = VALUES(account_id),
category_id = VALUES(category_id),
bill_type = VALUES(bill_type),
amount = VALUES(amount),
bill_date = VALUES(bill_date),
merchant_name = VALUES(merchant_name),
remark = VALUES(remark),
source_type = VALUES(source_type);

INSERT INTO sys_notice (id, title, content, cover_url, publish_status) VALUES
(1, '六月预算提醒', '<p>本月餐饮与居家支出已进入观察区间，建议按周回顾消费结构。</p>', '', 1),
(2, '票据识别使用建议', '<p>上传购物小票或电子发票截图后，请核对金额、日期、账户和分类，再保存为正式账单。</p>', '', 1),
(3, '家庭账本协作提示', '<p>家庭共用账本建议按月维护总预算，并将大额居家支出备注清楚，便于月底复盘。</p>', '', 1)
ON DUPLICATE KEY UPDATE title = VALUES(title), content = VALUES(content), publish_status = VALUES(publish_status);

INSERT INTO sys_dict (id, dict_type, dict_label, dict_value, sort_order, status) VALUES
(1, 'currency', '人民币', 'CNY', 1, 1),
(2, 'account_type', '现金', 'CASH', 1, 1),
(3, 'account_type', '银行卡', 'BANK', 2, 1),
(4, 'account_type', '支付宝', 'ALIPAY', 3, 1),
(5, 'account_type', '微信', 'WECHAT', 4, 1),
(6, 'bill_type', '支出', 'EXPENSE', 1, 1),
(7, 'bill_type', '收入', 'INCOME', 2, 1)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), sort_order = VALUES(sort_order), status = VALUES(status);
