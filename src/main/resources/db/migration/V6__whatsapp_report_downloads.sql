CREATE TABLE whatsapp_report_downloads (
    token VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    data BYTEA NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_whatsapp_report_downloads_expires ON whatsapp_report_downloads(expires_at);
