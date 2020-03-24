# Content jobs runtime
# Content requires long running jobs that could be split into parallel
# This is a good use case for spark.

resource "kubernetes_namespace" "content" {
  metadata {
    annotations = {
      name = "content"
    }

    name = "content"
  }
}

resource "helm_release" "spark" {
  name       = "spark"
  repository = "https://charts.bitnami.com/bitnami"
  chart      = "spark"
  version    = "1.2.10"
  namespace  = "content"

  depends_on = [
    kubernetes_namespace.content
  ]
}
