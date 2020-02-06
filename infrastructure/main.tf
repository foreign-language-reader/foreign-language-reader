resource "aws_vpc" "main" {
  cidr_block = var.cidr_block
}

data "aws_availability_zones" "available" {}

resource "aws_subnet" "private" {
  count             = 2
  cidr_block        = cidrsubnet(aws_vpc.main.cidr_block, 4, count.index)
  availability_zone = data.aws_availability_zones.available.names[count.index]
  vpc_id            = aws_vpc.main.id

  tags = {
    Name = "API-Private-Subnet-${count.index}"
  }
}

resource "aws_subnet" "public" {
  count                   = 2
  cidr_block              = cidrsubnet(aws_vpc.main.cidr_block, 4, 2 + count.index)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  vpc_id                  = aws_vpc.main.id
  map_public_ip_on_launch = true

  tags = {
    Name = "API-Public-Subnet-${count.index}"
  }
}

resource "aws_network_acl" "main" {
  vpc_id     = aws_vpc.main.id
  subnet_ids = [aws_subnet.public[0].id, aws_subnet.public[1].id]
}

resource "aws_network_acl_rule" "block_all_inbound_unless_allowed" {
  network_acl_id = aws_network_acl.main.id
  rule_number    = 101
  egress         = false
  protocol       = "all"
  cidr_block     = "0.0.0.0/0"
  rule_action    = "deny"
}

resource "aws_network_acl_rule" "allow_all_outbound" {
  network_acl_id = aws_network_acl.main.id
  rule_number    = 102
  egress         = true
  protocol       = "all"
  cidr_block     = "0.0.0.0/0"
  rule_action    = "allow"
}

resource "aws_network_acl_rule" "inbound_http_traffic" {
  network_acl_id = aws_network_acl.main.id
  rule_number    = 200
  egress         = false
  protocol       = "tcp"
  cidr_block     = "0.0.0.0/0"
  rule_action    = "allow"
  from_port      = 80
  to_port        = 80
}

resource "aws_network_acl_rule" "inbound_tls_traffic" {
  network_acl_id = aws_network_acl.main.id
  rule_number    = 201
  egress         = false
  protocol       = "tcp"
  cidr_block     = "0.0.0.0/0"
  rule_action    = "allow"
  from_port      = 443
  to_port        = 443
}

resource "aws_network_acl_rule" "inbound_ssh_traffic" {
  network_acl_id = aws_network_acl.main.id
  rule_number    = 202
  egress         = false
  protocol       = "tcp"
  cidr_block     = "0.0.0.0/0"
  rule_action    = "allow"
  from_port      = 22
  to_port        = 22
}

module "api" {
  source             = "./api"
  env                = var.env
  instance_size      = var.instance_size
  private_subnet_ids = [aws_subnet.private[0].id, aws_subnet.private[1].id]
  puhlic_subnet_ids  = [aws_subnet.public[0].id, aws_subnet.public[1].id]
  rds_username       = var.rds_username
  rds_password       = var.rds_password
}

module "frontend" {
  source = "./frontend"
  env    = var.env
}

module "vocabulary-lambda" {
  source = "./vocabulary"
  env    = var.env
}
