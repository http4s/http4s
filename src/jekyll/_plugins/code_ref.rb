require 'rouge'

module Jekyll
  class CodeRefTag < Liquid::Tag
    def initialize(tag_name, args, tokens)
      all = args.strip.reverse.split(' ')
      item = all.first.reverse
      file = all[1..-1].join(" ").reverse
      raise "You need to specify a name for the section to fetch" if all.size == 1
      super
      
      @file = file
      @item = item

    end

    def add_code_tags(code, lang)
      code = code.to_s
      code = code.sub(/<pre>/, "<pre><code class=\"language-#{lang}\" data-lang=\"#{lang}\">")
      code = code.sub(/<\/pre>/,"</code></pre>")
    end

    def strip_margin(text, spaces)
      lines = text.strip.split("\n")
      lines[0] << "\n" << lines[1..-1].map { |l| l[spaces..-1] }.join("\n")
    end

    def render(context)
      return "Code ref file '#{@file}' does not exist." unless File.exist?(@file)

      indented = (File.read(@file).match(/(?:\/\/\/|###)\s*code_ref\s*\:\s*#{@item}(.*?)(?:\/{3}|###)\s*end_code_ref/mi)||[])[1]
      spaces = indented[1..-1].match(/(\s*)[^ ]/)[1].size
      code = spaces == 0 ? indented : strip_margin(indented, spaces)

      return "No code matched the key #{@item} in #{@file}" unless code

      lexer = Rouge::Lexer.find(File.extname(@file)).aliases[0]
      formatter = Rouge::Formatters::HTML.new(opts.merge(line_numbers: true))
      highlighted = formatter.format(lexer.lex(source)) #Pygments.highlight(code, :lexer => lexer, :options => { :style => "default", :encoding => 'utf-8'})
      add_code_tags(highlighted, lexer)
    end
  end

end

Liquid::Template.register_tag('code_ref', Jekyll::CodeRefTag)