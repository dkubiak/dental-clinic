# RDS/Aurora module — PostgreSQL MUST be AWS-managed (Technology Stack Constraints), never
# self-hosted in-cluster. Aurora PostgreSQL with Multi-AZ so failover covers the database failure
# domain for this high-risk module (plan.md Risk Tier section — auth-service depends on this
# instance and Spring Session JDBC also lives here).

terraform {
  required_version = ">= 1.9"
}

resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-db-subnets"
  subnet_ids = var.private_subnet_ids
  tags       = var.tags
}

resource "aws_security_group" "db" {
  name_prefix = "${var.name_prefix}-db-"
  vpc_id      = var.vpc_id
  description = "Allow Postgres access from the EKS node security group only."

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = var.allowed_security_group_ids
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = var.tags
}

resource "aws_rds_cluster" "this" {
  cluster_identifier     = "${var.name_prefix}-auth-db"
  engine                 = "aurora-postgresql"
  engine_version         = var.engine_version
  database_name          = var.database_name
  master_username        = var.master_username
  manage_master_user_password = true
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  storage_encrypted      = true # RODO Art. 9-adjacent data at rest (Principle II / FR-013)
  deletion_protection    = var.deletion_protection

  # Multi-AZ failover: one writer instance + reader instance(s) below cover this.
  skip_final_snapshot = var.skip_final_snapshot

  tags = var.tags
}

resource "aws_rds_cluster_instance" "instances" {
  count              = var.instance_count
  identifier         = "${var.name_prefix}-auth-db-${count.index}"
  cluster_identifier = aws_rds_cluster.this.id
  instance_class     = var.instance_class
  engine             = aws_rds_cluster.this.engine
  engine_version     = aws_rds_cluster.this.engine_version

  tags = var.tags
}
