data "aws_subnet" "main" {
  id = "${var.subnet_id}"
}

resource "aws_security_group" "database" {
  name        = "database-${var.env}"
  description = "Database security group for ${var.env}"
  vpc_id      = data.aws_subnet.main.vpc_id

  ingress {
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = [data.aws_subnet.main.cidr_block]
  }

  egress {
    protocol    = "all"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_instance" "default" {
  allocated_storage      = 20
  max_allocated_storage  = 1000
  storage_type           = "gp2"
  engine                 = "mysql"
  engine_version         = "5.7"
  instance_class         = "db.${instance_size}"
  name                   = "foreign-language-reader-${env}"
  username               = var.rds_username
  password               = var.rds_password
  parameter_group_name   = "default.mysql5.7"
  deletion_protection    = true
  storage_encrypted      = true
  vpc_security_group_ids = [aws_security_group.database]
}
