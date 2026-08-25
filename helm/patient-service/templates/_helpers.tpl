{{- define "patient-service.name" -}}
patient-service
{{- end -}}

{{- define "patient-service.labels" -}}
app.kubernetes.io/name: {{ include "patient-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}
