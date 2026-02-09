{{/*
Chart full name: release-chart
*/}}
{{- define "kafka-platform.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels applied to all resources.
*/}}
{{- define "kafka-platform.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: kafka-platform
{{- end }}

{{/*
Selector labels for a specific service.
Expects a dict with "name" and "root" keys.
Usage: include "kafka-platform.selectorLabels" (dict "name" $name "root" $)
*/}}
{{- define "kafka-platform.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end }}
